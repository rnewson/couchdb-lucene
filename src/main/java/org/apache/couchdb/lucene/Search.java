package org.apache.couchdb.lucene;

import java.util.Scanner;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.NIOFSDirectory;

/**
 * Search entry point.
 */
public final class Search {

	private static final Logger logger = LogManager.getLogger(Search.class);

	public static void main(final String[] args) throws Exception {
		IndexReader reader = null;
		IndexSearcher searcher = null;

		final Scanner scanner = new Scanner(System.in);
		while (scanner.hasNextLine()) {
			if (reader == null) {
				// Open a reader and searcher if index exists.
				if (IndexReader.indexExists(Config.INDEX_DIR)) {
					reader = IndexReader.open(NIOFSDirectory.getDirectory(Config.INDEX_DIR), true);
					searcher = new IndexSearcher(reader);
				}
			} else {
				// Refresh reader and searcher if necessary.
				final IndexReader newReader = reader.reopen();
				if (reader != newReader) {
					final IndexReader oldReader = reader;
					reader = newReader;
					searcher = new IndexSearcher(reader);
					oldReader.close();
				}
			}

			// Process search request if index exists.
			if (searcher == null) {
				System.out.println("{\"code\":503,\"body\":\"couchdb-lucene not available.\"}");
			} else {
				final SearchRequest request = new SearchRequest(scanner.nextLine());
				final String result = request.execute(searcher);
				System.out.println(result);
			}
		}
	}

	/*
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
	*/

}
