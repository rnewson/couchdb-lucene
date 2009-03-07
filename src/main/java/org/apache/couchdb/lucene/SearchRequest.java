package org.apache.couchdb.lucene;

import static java.lang.Math.min;

import java.io.IOException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.MapFieldSelector;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.BooleanClause.Occur;

public final class SearchRequest {

	private static final FieldSelector FS = new MapFieldSelector(new String[] { Config.ID, Config.REV });

	private static final Database DB = new Database(Config.DB_URL);

	private final String dbname;

	private final Query q;

	private final int skip;

	private final int limit;

	private final Sort sort;

	private final boolean debug;

	private final boolean include_docs;

	public SearchRequest(final String json) throws ParseException {
		final JSONObject obj = JSONObject.fromObject(json);
		final JSONObject info = obj.getJSONObject("info");
		final JSONObject query = obj.getJSONObject("query");

		this.dbname = info.getString("db_name");
		this.skip = query.optInt("skip", 0);
		this.limit = query.optInt("limit", 25);
		this.debug = query.optBoolean("debug", false);
		this.include_docs = query.optBoolean("include_docs", false);

		// Parse query.
		final BooleanQuery q = new BooleanQuery();
		q.add(new TermQuery(new Term(Config.DB, this.dbname)), Occur.MUST);
		q.add(Config.QP.parse(query.getString("q")), Occur.MUST);
		this.q = q;

		// Parse sort order.
		final String sort = query.optString("sort", null);
		if (sort == null) {
			this.sort = null;
		} else {
			if (sort.indexOf(",") != -1) {
				this.sort = new Sort(sort.split(","));
			} else {
				this.sort = new Sort(sort, !query.optBoolean("asc", true));
			}
		}
	}

	public String execute(final IndexSearcher searcher) throws IOException {
		final TopDocs td;
		final StopWatch stopWatch = new StopWatch();
		// Perform search.
		if (sort == null) {
			td = searcher.search(q, null, skip + limit);
		} else {
			td = searcher.search(q, null, skip + limit, sort);
		}
		stopWatch.lap("search");
		// Fetch matches (if any).
		final int max = min(td.totalHits, limit);
		final JSONArray rows = new JSONArray();
		for (int i = skip; i < skip + max; i++) {
			final Document doc = searcher.doc(td.scoreDocs[i].doc, FS);
			final JSONObject obj = new JSONObject();
			// Include basic details.
			obj.element("_id", doc.get(Config.ID));
			obj.element("_rev", doc.get(Config.REV));
			obj.element("score", td.scoreDocs[i].score);
			// Include sort order (if any).
			if (td instanceof TopFieldDocs) {
				final FieldDoc fd = (FieldDoc) ((TopFieldDocs) td).scoreDocs[i];
				obj.element("sort_order", fd.fields);
			}
			// Fetch document (if requested).
			if (include_docs) {
				obj.element("doc", DB.getDoc(dbname, obj.getString("_id"), obj.getString("_rev")));
			}
			rows.add(obj);
		}
		stopWatch.lap("fetch");

		final JSONObject json = new JSONObject();
		json.element("q", q.toString(Config.DEFAULT_FIELD));
		json.element("sort", sort);
		json.element("skip", skip);
		json.element("limit", limit);
		json.element("total_rows", td.totalHits);
		json.element("search_duration", stopWatch.getElapsed("search"));
		json.element("fetch_duration", stopWatch.getElapsed("fetch"));
		json.element("rows", rows);

		final JSONObject result = new JSONObject();
		result.element("code", 200);

		if (debug) {
			result.put("body", "<pre>" + StringEscapeUtils.escapeHtml(json.toString(2)) + "</pre>");
		} else {
			result.put("json", json);
		}

		return result.toString();
	}

}
