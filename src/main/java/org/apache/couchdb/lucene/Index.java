package org.apache.couchdb.lucene;

import static java.lang.Math.min;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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
import org.apache.lucene.document.NumberTools;
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
							writer.close();
							progress.save();
							log.info("Committed updates.");

							// TODO needs mutex.
							final IndexReader oldReader = reader;
							reader = reader.reopen();
							if (reader != oldReader) {
								searcher = new IndexSearcher(reader);
								oldReader.close();
							}
						} else {
							writer.rollback();
							log.debug("No changes.");
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
			final long start = from;

			boolean changed = false;
			while (from < info.getUpdateSeq()) {
				final JSONObject obj = db.getAllDocsBySeq(dbname, from, Config.BATCH_SIZE);
				final JSONArray rows = obj.getJSONArray("rows");
				for (int i = 0, max = rows.size(); i < max; i++) {
					updateDocument(writer, dbname, rows.getJSONObject(i));
					changed = true;

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

			// Standard properties.
			doc.add(token(Config.DB, dbname, false));

			// Custom properties
			add(doc, null, obj.getJSONObject("doc"), false);

			// write it
			writer.addDocument(doc);
		}

		private void add(final Document out, final String key, final Object value, final boolean store) {
			if (value instanceof JSONObject) {
				final JSONObject json = (JSONObject) value;
				for (final Object obj : json.keySet()) {
					add(out, (String) obj, json.get(obj), false);
				}
			} else if (value instanceof String) {
				try {
					final Date date = DATE_FORMAT.parse((String) value);
					out.add(token(key, NumberTools.longToString(date.getTime()), false));
				} catch (final java.text.ParseException e) {
					out.add(text(key, (String) value, false));
				}
			} else if (value instanceof Integer) {
				out.add(token(key, NumberTools.longToString((Integer) value), false));
			} else if (value instanceof Long) {
				out.add(token(key, NumberTools.longToString((Long) value), false));
			} else if (value instanceof Boolean) {
				out.add(token(key, Boolean.toString((Boolean) value), false));
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

	private final Timer timer = new Timer("IndexUpdater");

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
		log.info("couchdb-lucene is starting.");
		this.progress.load();
		this.reader = IndexReader.open(dir, true);
		this.searcher = new IndexSearcher(this.reader);
		timer.schedule(new IndexUpdateTask(), 0, Config.REFRESH_INTERVAL);
		log.info("couchdb-lucene has started.");
	}

	public void stop() throws IOException {
		log.info("couchdb-lucene is stopping.");
		timer.cancel();
		this.searcher.close();
		log.info("couchdb-lucene is stopped.");
	}

	public String query(final String db, final String query, final String sort, final int skip, final int limit)
			throws IOException, ParseException {
		if (log.isDebugEnabled()) {
			final String msg = String.format("db:%s, query:%s, sort:%s, skip:%,d, limit:%,d\n", db, query, sort, skip,
					limit);
			log.debug(msg);
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
				sort_order.add(field.getField());
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
		result.element("json", json);

		return result.toString();
	}

	private IndexWriter newWriter() throws IOException {
		final IndexWriter result = new IndexWriter(dir, Config.ANALYZER, MaxFieldLength.UNLIMITED);
		result.setUseCompoundFile(false);
		result.setRAMBufferSizeMB(Config.RAM_BUF);
		return result;
	}

}
