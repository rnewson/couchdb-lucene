package org.apache.couchdb.lucene;

import static java.lang.Math.min;
import static org.apache.couchdb.lucene.Utils.text;
import static org.apache.couchdb.lucene.Utils.token;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.MapFieldSelector;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
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

	private void openReader() throws IOException {
		final IndexReader oldReader;
		synchronized (mutex) {
			oldReader = this.reader;
		}

		final IndexReader newReader;
		if (oldReader == null) {
			newReader = IndexReader.open(dir, true);
		} else {
			newReader = oldReader.reopen();
		}

		if (oldReader != newReader) {
			synchronized (mutex) {
				this.reader = newReader;
				this.reader.incRef();
				this.searcher = new IndexSearcher(this.reader);
			}
			if (oldReader != null) {
				oldReader.decRef();
			}
		}
	}

	private static final Logger log = LogManager.getLogger(Index.class);

	private static final Tika TIKA = new Tika();

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

							openReader();

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
				log.debug("index is missing or inconsistent, reindexing all documents for " + dbname);
				writer.deleteDocuments(new Term(Config.DB, dbname));
			}

			boolean changed = false;
			while (from < info.getUpdateSeq()) {
				final JSONObject obj = db.getAllDocsBySeq(dbname, from, Config.BATCH_SIZE);
				if (!obj.has("rows")) {
					log.error("no rows found (" + obj + ").");
					return false;
				}
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

			// Skip design documents.
			if (json.getString(Config.ID).startsWith("_design")) {
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
					final String url = db.url(String.format("%s/%s/%s", dbname, db.encode(id), db.encode(name)));
					final GetMethod get = new GetMethod(url);
					try {
						synchronized (db) {
							final int sc = Database.CLIENT.executeMethod(get);
							if (sc == 200) {
								TIKA.parse(get.getResponseBodyAsStream(), att.getString("content_type"), doc);
							} else {
								log.warn("Failed to retrieve attachment: " + sc);
							}
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
				log.warn(key + " was null.");
			} else {
				log.warn("Unsupported data type: " + value.getClass());
			}
		}

	}

	private static final FieldSelector FS = new MapFieldSelector(new String[] { Config.ID, Config.REV });

	private final Database db = new Database(Config.DB_URL);

	private final Timer timer = new Timer("IndexUpdater", true);

	private final Directory dir;

	private IndexReader reader;

	private IndexSearcher searcher;

	private final Progress progress;

	private final Object mutex = new Object();

	public Index() throws IOException {
		final File f = new File(Config.INDEX_DIR);
		dir = NIOFSDirectory.getDirectory(f);
		if (!IndexReader.indexExists(dir)) {
			newWriter().close();
		}
		this.progress = new Progress(f);
	}

	public void start() throws Exception {
		log.info("couchdb-lucene is starting.");
		if (IndexWriter.isLocked(dir)) {
			log.warn("Forcibly unlocking locked index at startup.");
			IndexWriter.unlock(dir);
		}

		Index.this.progress.load();
		openReader();
		// Warm the searcher.
		query("nomatch", "dummy_field:dummy_value", null, true, 0, 5, false, false);

		log.info("couchdb-lucene is started.");

		timer.schedule(new IndexUpdateTask(), 0, Config.REFRESH_INTERVAL);
	}

	public void stop() throws IOException {
		log.info("couchdb-lucene is stopping.");
		timer.cancel();
		this.reader.close();
		log.info("couchdb-lucene is stopped.");
	}

	public String query(final String dbname, final String query, final String sort_fields, final boolean ascending,
			final int skip, final int limit, final boolean include_docs, final boolean debug) throws IOException,
			ParseException {
		if (limit > Config.MAX_LIMIT) {
			return Utils.error("limit of " + limit + " exceeds maximum limit of " + Config.MAX_LIMIT);
		}

		final BooleanQuery bq = new BooleanQuery();
		bq.add(new TermQuery(new Term(Config.DB, dbname)), Occur.MUST);
		bq.add(parse(query), Occur.MUST);

		final IndexSearcher searcher;
		synchronized (mutex) {
			searcher = this.searcher;
		}

		searcher.getIndexReader().incRef();
		final TopDocs td;
		final long start = System.nanoTime();
		try {
			if (sort_fields == null) {
				td = searcher.search(bq, null, skip + limit);
			} else {
				final Sort sort;
				if (sort_fields.indexOf(",") != -1) {
					sort = new Sort(sort_fields.split(","));
				} else {
					sort = new Sort(sort_fields, !ascending);
				}
				td = searcher.search(bq, null, skip + limit, sort);
			}
		} finally {
			searcher.getIndexReader().decRef();
		}
		final long search_duration = System.nanoTime() - start;

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
				final JSONObject col = new JSONObject();
				col.element("field", field.getField());
				col.element("reverse", field.getReverse());

				final String type;
				switch (field.getType()) {
				case SortField.DOC:
					type = "doc";
					break;
				case SortField.SCORE:
					type = "score";
					break;
				case SortField.INT:
					type = "int";
					break;
				case SortField.LONG:
					type = "long";
					break;
				case SortField.BYTE:
					type = "byte";
					break;
				case SortField.CUSTOM:
					type = "custom";
					break;
				case SortField.DOUBLE:
					type = "double";
					break;
				case SortField.FLOAT:
					type = "float";
					break;
				case SortField.SHORT:
					type = "short";
					break;
				case SortField.STRING:
					type = "string";
					break;
				default:
					type = "unknown";
					break;
				}
				col.element("type", type);
				sort_order.add(col);
			}
			json.element("sort_order", sort_order);
		}

		final int max = min(td.totalHits, limit);
		final String[] fetch_ids = include_docs ? new String[max] : null;
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
			if (fetch_ids != null) {
				fetch_ids[i - skip] = obj.getString(Config.ID);
			}
			if (include_docs) {
				obj.element("doc", db.getDoc(dbname, obj.getString("_id"), obj.getString("_rev")));
			}
			rows.add(obj);
		}

		if (fetch_ids != null) {
			final JSONObject fetch_docs = db.getDocs(dbname, fetch_ids);
			final JSONArray arr = fetch_docs.getJSONArray("rows");
			for (int i = 0; i < max; i++) {
				rows.getJSONObject(i).element("doc", arr.getJSONObject(i).getJSONObject("doc"));
			}
		}

		json.element("rows", rows);
		final long total_duration = System.nanoTime() - start;

		final JSONObject result = new JSONObject();
		result.element("code", 200);

		if (debug) {
			final StringBuilder builder = new StringBuilder(500);
			// build basic lines.
			builder.append("<dl>");
			builder.append("<dt>database name</dt><dd>" + dbname + "</dd>");
			builder.append("<dt>query</dt><dd>" + bq + "</dd>");
			builder.append("<dt>sort</dt><dd>" + sort_fields + "</dd>");
			builder.append("<dt>skip</dt><dd>" + skip + "</dd>");
			builder.append("<dt>limit</dt><dd>" + limit + "</dd>");
			builder.append("<dt>total_rows</dt><dd>" + json.getInt("total_rows") + "</dd>");
			if (json.get("sort_order") != null) {
				builder.append("<dt>sort_order</dt><dd>" + json.get("sort_order") + "</dd>");
			}
			builder.append("<dt>search duration</dt><dd>"
					+ DurationFormatUtils.formatDurationHMS(search_duration / 1000000) + "</dd>");
			builder.append("<dt>total duration</dt><dd>"
					+ DurationFormatUtils.formatDurationHMS(total_duration / 1000000) + "</dd>");
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

	private Query parse(final String query) throws ParseException {
		return Config.QP.parse(query);
	}

	private IndexWriter newWriter() throws IOException {
		final IndexWriter result = new IndexWriter(dir, Config.ANALYZER, MaxFieldLength.UNLIMITED);
		result.setUseCompoundFile(false);
		result.setRAMBufferSizeMB(Config.RAM_BUF);
		return result;
	}

}
