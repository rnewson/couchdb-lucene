package com.github.rnewson.couchdb.lucene;

/**
 * Copyright 2009 Robert Newson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static com.github.rnewson.couchdb.lucene.Utils.docQuery;
import static com.github.rnewson.couchdb.lucene.Utils.text;
import static com.github.rnewson.couchdb.lucene.Utils.token;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Scanner;

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

public final class Index {

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z '('z')'");

	private static final Database DB = new Database(Config.DB_URL);

	private static final Tika TIKA = new Tika();

	static class Indexer implements Runnable {

		private boolean isStale = true;

		private final Directory dir;

		public Indexer(final Directory dir) {
			this.dir = dir;
		}

		public synchronized boolean isStale() {
			return isStale;
		}

		public synchronized void setStale(final boolean isStale) {
			this.isStale = isStale;
		}

		public synchronized boolean setStale(final boolean expected, final boolean update) {
			if (isStale == expected) {
				isStale = update;
				return true;
			}
			return false;
		}

		public void run() {
			while (true) {
				if (!isStale()) {
					sleep();
				} else {
					final long commitBy = System.currentTimeMillis() + Config.COMMIT_MAX;
					boolean quiet = false;
					while (!quiet && System.currentTimeMillis() < commitBy) {
						setStale(false);
						sleep();
						quiet = !isStale();
					}

					/*
					 * Either no update has occurred in the last COMMIT_MIN
					 * interval or continual updates have occurred for
					 * COMMIT_MAX interval. Either way, index all changes and
					 * commit.
					 */
					try {
						updateIndex();
					} catch (final IOException e) {
						Log.errlog(e);
					}
				}
			}
		}

		private void sleep() {
			try {
				Thread.sleep(Config.COMMIT_MIN);
			} catch (final InterruptedException e) {
				Log.errlog("Interrupted while sleeping, indexer is exiting.");
			}
		}

		private IndexWriter newWriter() throws IOException {
			final IndexWriter result = new IndexWriter(Config.INDEX_DIR, Config.ANALYZER, MaxFieldLength.UNLIMITED);

			// Customize merge policy.
			final LogByteSizeMergePolicy mp = new LogByteSizeMergePolicy();
			mp.setMergeFactor(5);
			mp.setMaxMergeMB(1000);
			result.setMergePolicy(mp);

			// Customize other settings.
			result.setUseCompoundFile(false);
			result.setRAMBufferSizeMB(Config.RAM_BUF);

			return result;
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
			boolean expunge = false;
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
								delete(term.text(), progress, writer);
								commit = true;
								expunge = true;
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
					if (expunge) {
						writer.expungeDeletes();
					}
					writer.close();

					final IndexReader reader = IndexReader.open(dir);
					try {
						Log.errlog("Committed changes to index (%,d documents in index, %,d deletes).", reader
								.numDocs(), reader.numDeletedDocs());
					} finally {
						reader.close();
					}
				} else {
					writer.rollback();
				}
			}
		}

		private boolean updateDatabase(final IndexWriter writer, final String dbname, final Progress progress,
				final Rhino rhino) throws HttpException, IOException {
			final long target_seq = DB.getInfo(dbname).getLong("update_seq");

			final String cur_sig = progress.getSignature(dbname);
			final String new_sig = rhino == null ? Progress.NO_SIGNATURE : rhino.getSignature();

			boolean result = false;

			// Reindex the database if sequence is 0 or signature changed.
			if (progress.getSeq(dbname) == 0 || cur_sig.equals(new_sig) == false) {
				Log.errlog("Indexing '%s' from scratch.", dbname);
				delete(dbname, progress, writer);
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
                        writer.deleteDocuments(docQuery(dbname, row.getString("id")));
						updateDocument(writer, dbname, rows.getJSONObject(i), rhino);
						result = true;
					}

					// Deleted document.
					if (value != null && value.optBoolean("deleted")) {
                        writer.deleteDocuments(docQuery(dbname, row.getString("id")));
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

		private void delete(final String dbname, final Progress progress, final IndexWriter writer) throws IOException {
			writer.deleteDocuments(new Term(Config.DB, dbname));
			progress.remove(dbname);
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

			// Discard _rev
			json.remove("_rev");
			// Remove _id.
			final String id = (String) json.remove(Config.ID);

			// Index db, id and uid as tokens.
			doc.add(token(Config.DB, dbname, false));
			doc.add(token(Config.ID, id, true));

			// Attachments
			if (json.has("_attachments")) {
				final JSONObject attachments = (JSONObject) json.remove("_attachments");
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

			// Index all attributes.
			add(null, doc, null, json, false);

			// write it
			writer.addDocument(doc);
		}

		private void add(final String prefix, final Document out, final String key, final Object value,
				final boolean store) {
			final String prefixed_key = prefix != null ? prefix + "." + key : key;

			if (value instanceof JSONObject) {
				final JSONObject json = (JSONObject) value;
				for (final Object obj : json.keySet()) {
					add(prefixed_key, out, (String) obj, json.get(obj), store);
				}
			} else if (value instanceof JSONArray) {
				final JSONArray arr = (JSONArray) value;
				for (int i = 0, max = arr.size(); i < max; i++) {
					add(prefixed_key, out, key, arr.get(i), store);
				}
			} else if (value instanceof String) {
				try {
					final Date date = DATE_FORMAT.parse((String) value);
					out.add(new Field(prefixed_key, (String) value, Store.YES, Field.Index.NO));
					out.add(new Field(prefixed_key, Long.toString(date.getTime()), Store.NO,
							Field.Index.NOT_ANALYZED_NO_NORMS));
				} catch (final java.text.ParseException e) {
					out.add(text(prefixed_key, (String) value, store));
				}
			} else if (value instanceof Number) {
				out.add(token(prefixed_key, value.toString(), store));
			} else if (value instanceof Boolean) {
				out.add(token(prefixed_key, value.toString(), store));
			} else if (value == null) {
				Log.errlog("%s was null.", key);
			} else {
				Log.errlog("Unsupported data type: %s.", value.getClass());
			}
		}

	}

	public static void main(String[] args) throws Exception {
		final Indexer indexer = new Indexer(FSDirectory.getDirectory(Config.INDEX_DIR));
		final Thread thread = new Thread(indexer, "index");
		thread.setDaemon(true);
		thread.start();

		final Scanner scanner = new Scanner(System.in);
		while (scanner.hasNextLine()) {
			final String line = scanner.nextLine();
			final JSONObject obj = JSONObject.fromObject(line);
			if (obj.has("type") && obj.has("db")) {
				indexer.setStale(true);
			}
		}
	}

}
