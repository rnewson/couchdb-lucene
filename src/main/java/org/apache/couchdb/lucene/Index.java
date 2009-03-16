package org.apache.couchdb.lucene;

import static org.apache.couchdb.lucene.Utils.text;
import static org.apache.couchdb.lucene.Utils.token;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LogByteSizeMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * High-level wrapper class over Lucene.
 */
public final class Index {

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z '('z')'");

	private static final Database DB = new Database(Config.DB_URL);

	private static final Tika TIKA = new Tika();

	private static final Object MUTEX = new Object();

	private static final Timer TIMER = new Timer("Timer", true);

	private static class CheckpointTask extends TimerTask {

		@Override
		public void run() {
			wakeupIndexer();
		}

	}

	private static class Indexer implements Runnable {

		private Directory dir;

		private boolean running = true;

		private long lastCommit = System.currentTimeMillis();

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

		private synchronized void updateIndex() throws IOException {
			if (IndexWriter.isLocked(dir)) {
				Log.errlog("Forcibly unlocking locked index at startup.");
				IndexWriter.unlock(dir);
			}

			final String[] dbnames = DB.getAllDatabases();
			Arrays.sort(dbnames);

			Rhino rhino = null;

			boolean commit = false;
			final IndexWriter writer = newWriter();
			final Progress progress = new Progress();
			try {
				final IndexReader reader = IndexReader.open(dir);
				try {
					// Load status.
					progress.load(reader);

					// Remove documents from deleted databases.
					final TermEnum terms = reader.terms(new Term(Config.DB, ""));
					try {
						do {
							final Term term = terms.term();
							if (term == null || Config.DB.equals(term.field()) == false)
								break;
							if (Arrays.binarySearch(dbnames, term.text()) < 0) {
								Log.errlog("Database '%s' has been deleted," + " removing all documents from index.",
										term.text());
								delete(term.text(), writer);
								commit = true;
							}
						} while (terms.next());
					} finally {
						terms.close();
					}
				} finally {
					reader.close();
				}

				// Update all extant databases.
				for (final String dbname : dbnames) {
					// Database might supply a transformation function.
					final JSONObject designDoc = DB.getDoc(dbname, "_design/lucene");
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
			} catch (final Exception e) {
				Log.errlog(e);
				commit = false;
			} finally {
				if (commit) {
					progress.save(writer);
					writer.close();
					lastCommit = System.currentTimeMillis();

					final IndexReader reader = IndexReader.open(dir);
					try {
						Log.errlog("Committed changes to index (%,d documents in index, %,d deletes).", reader
								.numDocs(), reader.numDeletedDocs());
					} finally {
						reader.close();
					}
				} else {
					Log.errlog("Closing writer without changing index.");
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

			// Customize merge policy.
			final LogByteSizeMergePolicy mp = new LogByteSizeMergePolicy();
			mp.setMergeFactor(5);
			mp.setMaxMergeMB(1000);
			result.setMergePolicy(mp);

			// Customer other settings.
			result.setUseCompoundFile(false);
			result.setRAMBufferSizeMB(Config.RAM_BUF);

			return result;
		}

		private boolean updateDatabase(final IndexWriter writer, final String dbname, final Progress progress,
				final Rhino rhino) throws HttpException, IOException {
			final long cur_seq = progress.getSeq(dbname);
			final long target_seq = DB.getInfo(dbname).getLong("update_seq");

			final boolean time_threshold_passed = (System.currentTimeMillis() - lastCommit) >= Config.TIME_THRESHOLD * 1000;
			final boolean change_threshold_passed = (target_seq - cur_seq) >= Config.CHANGE_THRESHOLD;
			
			if (!(time_threshold_passed || change_threshold_passed)) {
				return false;
			}

			final String cur_sig = progress.getSignature(dbname);
			final String new_sig = rhino == null ? Progress.NO_SIGNATURE : rhino.getSignature();

			boolean result = false;

			// Reindex the database if sequence is 0 or signature changed.
			if (progress.getSeq(dbname) == 0 || cur_sig.equals(new_sig) == false) {
				Log.errlog("Indexing '%s' from scratch.", dbname);
				delete(dbname, writer);
				progress.update(dbname, new_sig, 0);
				result = true;
			}

			long update_seq = progress.getSeq(dbname);
			while (update_seq < target_seq) {
				final JSONObject obj = DB.getAllDocsBySeq(dbname, update_seq, Config.BATCH_SIZE);

				if (!obj.has("rows")) {
					Log.errlog("no rows found (%s).", obj);
					return false;
				}

				// Process all rows
				final JSONArray rows = obj.getJSONArray("rows");
				for (int i = 0, max = rows.size(); i < max; i++) {
					final JSONObject row = rows.getJSONObject(i);
					final JSONObject value = row.optJSONObject("value");
					final JSONObject doc = row.optJSONObject("doc");

					// New or updated document.
					if (doc != null) {
						updateDocument(writer, dbname, rows.getJSONObject(i), rhino);
						result = true;
					}

					// Deleted document.
					if (value != null && value.optBoolean("deleted")) {
						writer.deleteDocuments(new Term(Config.ID, row.getString("id")));
						result = true;
					}

					update_seq = row.getLong("key");
				}
			}

			if (result) {
				progress.update(dbname, new_sig, update_seq);
				Log.errlog("%s: index caught up to %,d.", dbname, update_seq);
			}

			return result;
		}

		private void delete(final String dbname, final IndexWriter writer) throws IOException {
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
			// Discard _rev
			json.remove("_rev");

			// Index _id and _rev as tokens.
			doc.add(token(Config.ID, id, true));

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
					final Date date = DATE_FORMAT.parse((String) value);
					out.add(new Field(key, (String) value, Store.YES, Field.Index.NO));
					out.add(new Field(key, Long.toString(date.getTime()), Store.NO, Field.Index.NOT_ANALYZED_NO_NORMS));
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
		start("indexer", new Indexer());
		TIMER.schedule(new CheckpointTask(), Config.TIME_THRESHOLD * 1000, Config.TIME_THRESHOLD * 1000);

		final Scanner scanner = new Scanner(System.in);
		while (scanner.hasNextLine()) {
			final String line = scanner.nextLine();
			final JSONObject obj = JSONObject.fromObject(line);
			if (obj.has("type") && obj.has("db")) {
				wakeupIndexer();
			}
		}
	}

	private static void wakeupIndexer() {
		synchronized (MUTEX) {
			MUTEX.notify();
		}
	}

	private static void start(final String name, final Runnable runnable) {
		final Thread thread = new Thread(runnable, name);
		thread.setDaemon(true);
		thread.start();
	}

}
