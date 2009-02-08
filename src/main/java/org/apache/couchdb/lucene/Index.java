package org.apache.couchdb.lucene;

import static java.lang.Math.min;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.MapFieldSelector;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;

/**
 * High-level wrapper class over Lucene.
 */
public final class Index {

	private final class IndexStartTask extends TimerTask {

		@Override
		public void run() {
			log.info("couchdb-lucene is starting.");
			try {
				Index.this.progress.load();
				Index.this.reader = IndexReader.open(dir, true);
				Index.this.searcher = new IndexSearcher(Index.this.reader);
			} catch (IOException e) {
				System.out.println(Utils.throwableToJSON(e));
				log.info("couchdb-lucene failed to started.");
				return;
			}
			log.info("couchdb-lucene is started.");
		}

	}

	private static final Logger log = LogManager.getLogger(Index.class);

	private static final Pattern FLOAT_PATTERN = Pattern.compile("[-+]?[0-9]+\\.[0-9]+");

	private static final Pattern INTEGER_PATTERN = Pattern.compile("[-+]?[0-9]+");

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

	private class IndexUpdateTask extends TimerTask {

		@Override
		public void run() {
			IndexWriter writer = null;
			boolean commit = false;
			try {
				final String[] dbnames = db.getAllDatabases();
				writer = newWriter();
				for (final String dbname : dbnames) {
					commit |= updateDatabase(writer, dbname);
				}
			} catch (final IOException e) {
				log.warn("Exception while indexing.", e);
				commit = false;
			} finally {
				if (writer != null) {
					try {
						if (commit) {
							writer.commit();
							progress.save();
							log.info("Committed updates.");

							// TODO needs mutex.
							final IndexReader oldReader = reader;
							reader = reader.reopen();
							if (reader != oldReader) {
								searcher = new IndexSearcher(reader);
								oldReader.close();
							}

							if (reader.numDeletedDocs() >= Config.EXPUNGE_LIMIT) {
								writer.expungeDeletes();
							}
							writer.close();
						} else {
							writer.rollback();
						}
					} catch (final IOException e) {
						log.warn("Exception while committing.", e);
					}
				}
			}
		}

		private boolean updateDatabase(final IndexWriter writer, final String dbname) throws HttpException, IOException {
			final DbInfo info = db.getInfo(dbname);
			long from = progress.getProgress(dbname);
			long start = from;

			if (from > info.getUpdateSeq()) {
				start = from = -1;
				progress.setProgress(dbname, -1);
			}
			
			if (from == -1) {
				log.debug("index is inconsistent, reindexing all documents for " + dbname);
				writer.deleteDocuments(new Term(Config.DB, dbname));
			}

			boolean changed = false;
			while (from < info.getUpdateSeq()) {
				final JSONObject obj = db.getAllDocsBySeq(dbname, from, Config.BATCH_SIZE);
				final JSONArray rows = obj.getJSONArray("rows");
				for (int i = 0, max = rows.size(); i < max; i++) {
					final JSONObject row = rows.getJSONObject(i);
					final JSONObject value = row.optJSONObject("value");
					final JSONObject doc = row.optJSONObject("doc");

					if (doc != null) {
						updateDocument(writer, dbname, rows.getJSONObject(i));
						changed = true;
					}
					if (value != null && value.optBoolean("deleted")) {
						writer.deleteDocuments(new Term(Config.ID, row.getString("id")));
						changed = true;
					}
				}
				from += Config.BATCH_SIZE;
			}
			progress.setProgress(dbname, info.getUpdateSeq());

			if (changed) {
				log.debug(String.format("%s: index caught up from %,d to %,d.", dbname, start, info.getUpdateSeq()));
			}

			return changed;
		}

		private void updateDocument(final IndexWriter writer, final String dbname, final JSONObject obj)
				throws IOException {
			final Document doc = new Document();
			final JSONObject json = obj.getJSONObject("doc");

			// Standard properties.
			doc.add(token(Config.DB, dbname, false));
			add(doc, Config.ID, json.get(Config.ID), true);
			add(doc, Config.REV, json.get(Config.REV), true);

			// Custom properties
			add(doc, null, json, false);

			// write it
			writer.updateDocument(new Term(Config.ID, json.getString(Config.ID)), doc);
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
			} else if (value instanceof Integer) {
				out.add(token(key, Integer.toString((Integer) value), store));
			} else if (value instanceof Boolean) {
				out.add(token(key, Boolean.toString((Boolean) value), store));
			} else if (value instanceof JSONArray) {
				final JSONArray arr = (JSONArray) value;
				for (int i = 0, max = arr.size(); i < max; i++) {
					add(out, key, arr.get(i), store);
				}
			} else if (value == null) {
				log.warn(key + " was null.");
			} else {
				log.warn("Unsupported data type: " + value.getClass());
			}
		}

		private Field text(final String name, final String value, final boolean store) {
			return new Field(name, value, store ? Store.YES : Store.NO, Field.Index.ANALYZED);
		}

		private Field token(final String name, final String value, final boolean store) {
			return new Field(name, value, store ? Store.YES : Store.NO, Field.Index.NOT_ANALYZED_NO_NORMS);
		}

	}

	private static final FieldSelector FS = new MapFieldSelector(new String[] { Config.ID, Config.REV });

	private final Database db = new Database(Config.DB_URL);

	private final Timer timer = new Timer("IndexUpdater", true);

	private final Directory dir;

	private IndexReader reader;

	private IndexSearcher searcher;

	private final Progress progress;

	public Index() throws IOException {
		final File f = new File("lucene");
		dir = NIOFSDirectory.getDirectory(f);
		if (!IndexReader.indexExists(dir)) {
			newWriter().close();
		}
		this.progress = new Progress(f);
	}

	public void start() throws IOException {
		timer.schedule(new IndexStartTask(), 0);
		timer.schedule(new IndexUpdateTask(), 0, Config.REFRESH_INTERVAL);
	}

	public void stop() throws IOException {
		log.info("couchdb-lucene is stopping.");
		timer.cancel();
		this.searcher.close();
		log.info("couchdb-lucene is stopped.");
	}

	public String query(final String db, final String query, final String sort, final int skip, final int limit,
			final boolean debug) throws IOException, ParseException {
		if (reader == null) {
			return Utils.error("couchdb-lucene is not started yet.");
		}

		final BooleanQuery bq = new BooleanQuery();
		bq.add(new TermQuery(new Term(Config.DB, db)), Occur.MUST);
		bq.add(Config.QP.parse(query), Occur.MUST);

		final TopDocs td;
		if (sort == null)
			td = searcher.search(bq, null, skip + limit);
		else
			td = searcher.search(bq, null, skip + limit, new Sort(sort));

		TopFieldDocs tfd = null;
		if (td instanceof TopFieldDocs) {
			tfd = (TopFieldDocs) td;
		}

		final JSONObject json = new JSONObject();
		json.element("total_rows", td.totalHits);
		// Report on sorting order, if specified.
		if (tfd != null) {
			final JSONArray sort_order = new JSONArray();
			for (final SortField field : tfd.fields) {
				switch (field.getType()) {
				case SortField.DOC:
					sort_order.add("DOC");
					break;
				case SortField.SCORE:
					sort_order.add("SCORE");
					break;
				default:
					sort_order.add(field.getField());
					break;
				}
				// TODO include type and reverse.
			}
			json.element("sort_order", sort_order);
		}

		final int max = min(td.totalHits, limit);
		final JSONArray rows = new JSONArray();
		for (int i = skip; i < skip + max; i++) {
			final Document doc = searcher.doc(td.scoreDocs[i].doc, FS);
			final JSONObject obj = new JSONObject();
			obj.element("_id", doc.get(Config.ID));
			obj.element("_rev", doc.get(Config.REV));
			obj.element("score", td.scoreDocs[i].score);
			if (tfd != null) {
				final FieldDoc fd = (FieldDoc) tfd.scoreDocs[i];
				obj.element("sort_order", fd.fields);
			}
			rows.add(obj);
		}
		json.element("rows", rows);

		final JSONObject result = new JSONObject();
		result.element("code", 200);

		if (debug) {
			final StringBuilder builder = new StringBuilder(500);
			// build basic lines.
			builder.append("<dl>");
			builder.append("<dt>database name</dt><dd>" + db + "</dd>");
			builder.append("<dt>query</dt><dd>" + bq + "</dd>");
			builder.append("<dt>sort</dt><dd>" + sort + "</dd>");
			builder.append("<dt>skip</dt><dd>" + skip + "</dd>");
			builder.append("<dt>limit</dt><dd>" + limit + "</dd>");
			builder.append("<dt>total_rows</dt><dd>" + json.getInt("total_rows") + "</dd>");
			builder.append("<dt>rows</dt><dd>");
			builder.append("<ol start=\"" + skip + "\">");
			for (int i = 0; i < rows.size(); i++) {
				builder.append("<li>" + rows.get(i) + "</li>");
			}
			builder.append("</ol>");
			builder.append("</dd>");
			builder.append("</dl>");
			result.element("body", builder.toString());
		} else {
			result.element("json", json);
		}

		return result.toString();
	}

	private IndexWriter newWriter() throws IOException {
		final IndexWriter result = new IndexWriter(dir, Config.ANALYZER, MaxFieldLength.UNLIMITED);
		result.setUseCompoundFile(false);
		result.setRAMBufferSizeMB(Config.RAM_BUF);
		return result;
	}

}
