package org.apache.couchdb.lucene;

import static org.apache.couchdb.lucene.Utils.text;
import static org.apache.couchdb.lucene.Utils.token;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * High-level wrapper class over Lucene.
 */
public final class Index {

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

	private static final Database DB = new Database(Config.DB_URL);

	private static final Tika TIKA = new Tika();

	private static final Object MUTEX = new Object();

	private static final Map<String, Long> updates = new HashMap<String, Long>();

	private static class Indexer implements Runnable {

		private Directory dir;

		private boolean running = true;

		public void run() {
			try {
				this.dir = FSDirectory.getDirectory(Config.INDEX_DIR);
				while (running) {
					updateIndex();
					waitForUpdateNotification();
				}
			} catch (final IOException e) {
				Log.errlog(e);
			}
		}

		private void updateIndex() throws IOException {
			if (IndexWriter.isLocked(dir)) {
				Log.errlog("Forcibly unlocking locked index at startup.");
				IndexWriter.unlock(dir);
			}

			final String[] dbnames = DB.getAllDatabases();
			Arrays.sort(dbnames);

			Rhino rhino = null;

			boolean commit = false;
			boolean expunge = false;
			final IndexWriter writer = newWriter();
			Progress progress = null;
			try {
				// Delete all documents in non-extant databases.
				final IndexReader reader = IndexReader.open(dir);
				try {
					final TermEnum terms = reader.terms(new Term(Config.DB, ""));
					try {
						while (terms.next()) {
							final Term term = terms.term();
							if (!term.field().equals(Config.DB))
								break;
							if (Arrays.binarySearch(dbnames, term.text()) < 0) {
								Log.errlog("Database '%s' has been deleted," + " removing all documents from index.",
										term.text());
								delete(writer, term.text());
								commit = true;
								expunge = true;
							}
						}
					} finally {
						terms.close();
					}
				} finally {
					reader.close();
				}

				// Update all extant databases.
				progress = new Progress(dir);
				progress.load();
				for (final String dbname : dbnames) {
					// Database might supply a transformation function.
					final JSONObject designDoc = DB.getDoc(dbname, "_design/lucene", null);
					if (designDoc != null && designDoc.containsKey("transform")) {
						String transform = designDoc.getString("transform");
						// Strip start and end double quotes.
						transform = transform.replaceAll("^\"*", "");
						transform = transform.replaceAll("\"*$", "");
						rhino = new Rhino(transform);
					} else {
						rhino = null;
					}
					commit |= updateDatabase(writer, dbname, progress, rhino);
				}
			} catch (final IOException e) {
				Log.errlog(e);
				commit = false;
			} finally {
				if (commit) {
					if (expunge) {
						writer.expungeDeletes();
					}
					writer.close();
					Log.errlog("Committed changes to index.");
					progress.save();
				} else {
					writer.rollback();
				}
			}
		}

		private void waitForUpdateNotification() {
			synchronized (MUTEX) {
				try {
					MUTEX.wait();
				} catch (final InterruptedException e) {
					running = false;
				}
			}
		}

		private IndexWriter newWriter() throws IOException {
			final IndexWriter result = new IndexWriter(dir, Config.ANALYZER, MaxFieldLength.UNLIMITED);
			result.setUseCompoundFile(false);
			result.setRAMBufferSizeMB(Config.RAM_BUF);
			result.setMergeFactor(5);
			result.setMaxMergeDocs(1 * 1000 * 1000);
			return result;
		}

		private boolean updateDatabase(final IndexWriter writer, final String dbname, final Progress progress,
				final Rhino rhino) throws HttpException, IOException {
			final JSONObject info = DB.getInfo(dbname);
			final long update_seq = info.getLong("update_seq");

			long from = progress.getProgress(dbname);
			long start = from;

			if (from > update_seq) {
				start = from = -1;
				progress.setProgress(dbname, -1);
			}

			if (from == -1) {
				Log.errlog("Indexing '%s' from scratch.", dbname);
				delete(writer, dbname);
			}

			boolean changed = false;
			while (from < update_seq) {
				final JSONObject obj = DB.getAllDocsBySeq(dbname, from, Config.BATCH_SIZE);
				if (!obj.has("rows")) {
					Log.errlog("no rows found (%s).", obj);
					return false;
				}
				final JSONArray rows = obj.getJSONArray("rows");
				for (int i = 0, max = rows.size(); i < max; i++) {
					final JSONObject row = rows.getJSONObject(i);
					final JSONObject value = row.optJSONObject("value");
					final JSONObject doc = row.optJSONObject("doc");

					if (doc != null) {
						updateDocument(writer, dbname, rows.getJSONObject(i), rhino);
						changed = true;
					}
					if (value != null && value.optBoolean("deleted")) {
						writer.deleteDocuments(new Term(Config.ID, row.getString("id")));
						changed = true;
					}
				}
				from += Config.BATCH_SIZE;
			}
			progress.setProgress(dbname, update_seq);

			if (changed) {
				synchronized (MUTEX) {
					updates.remove(dbname);
				}
				Log.errlog("%s: index caught up from %,d to %,d.", dbname, start, update_seq);
			}

			return changed;
		}

		private void delete(final IndexWriter writer, final String dbname) throws IOException {
			writer.deleteDocuments(new Term(Config.DB, dbname));
		}

		private void updateDocument(final IndexWriter writer, final String dbname, final JSONObject obj,
				final Rhino rhino) throws IOException {
			final Document doc = new Document();
			JSONObject json = obj.getJSONObject("doc");

			// Skip design documents.
			if (json.getString(Config.ID).startsWith("_design")) {
				return;
			}

			// Pass through user-defined transformation (if any).
			if (rhino != null) {
				json = JSONObject.fromObject(rhino.parse(json.toString()));
				if (json.isNullObject())
					return;
			}

			// Standard properties.
			doc.add(token(Config.DB, dbname, false));
			final String id = (String) json.remove(Config.ID);
			final String rev = (String) json.remove(Config.REV);

			// Index _id and _rev as tokens.
			doc.add(token(Config.ID, id, true));
			doc.add(token(Config.REV, rev, true));

			// Index all attributes.
			add(doc, null, json, false);

			// Attachments
			if (json.has("_attachments")) {
				final JSONObject attachments = json.getJSONObject("_attachments");
				final Iterator it = attachments.keys();
				while (it.hasNext()) {
					final String name = (String) it.next();
					final JSONObject att = attachments.getJSONObject(name);
					final String url = DB.url(String.format("%s/%s/%s", dbname, DB.encode(id), DB.encode(name)));
					final GetMethod get = new GetMethod(url);
					try {
						final int sc = Database.CLIENT.executeMethod(get);
						if (sc == 200) {
							TIKA.parse(get.getResponseBodyAsStream(), att.getString("content_type"), doc);
						} else {
							Log.errlog("Failed to retrieve attachment: %d", sc);
						}
					} finally {
						get.releaseConnection();
					}
				}
			}

			// write it
			writer.updateDocument(new Term(Config.ID, id), doc);
		}

		private void add(final Document out, final String key, final Object value, final boolean store) {
			if (value instanceof JSONObject) {
				final JSONObject json = (JSONObject) value;
				for (final Object obj : json.keySet()) {
					add(out, (String) obj, json.get(obj), store);
				}
			} else if (value instanceof String) {
				try {
					DATE_FORMAT.parse((String) value);
					out.add(token(key, (String) value, store));
				} catch (final java.text.ParseException e) {
					out.add(text(key, (String) value, store));
				}
			} else if (value instanceof Number) {
				out.add(token(key, value.toString(), store));
			} else if (value instanceof Boolean) {
				out.add(token(key, value.toString(), store));
			} else if (value instanceof JSONArray) {
				final JSONArray arr = (JSONArray) value;
				for (int i = 0, max = arr.size(); i < max; i++) {
					add(out, key, arr.get(i), store);
				}
			} else if (value == null) {
				Log.errlog("%s was null.", key);
			} else {
				Log.errlog("Unsupported data type: %s.", value.getClass());
			}
		}

	}

	/**
	 * update notifications look like this;
	 * 
	 * {"type":"updated","db":"cas"}
	 * 
	 * type can be created, updated or deleted.
	 */
	public static void main(final String[] args) {
		final Runnable indexer = new Indexer();
		final Thread indexerThread = new Thread(indexer, "indexer");
		indexerThread.setDaemon(true);
		indexerThread.start();

		final Scanner scanner = new Scanner(System.in);
		while (scanner.hasNextLine()) {
			final String line = scanner.nextLine();
			final JSONObject obj = JSONObject.fromObject(line);
			if (obj.has("type") && obj.has("db")) {
				synchronized (MUTEX) {
					updates.put(obj.getString("db"), System.nanoTime());
					MUTEX.notify();
				}
			}
		}
	}

}
