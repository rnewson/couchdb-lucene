package org.apache.couchdb.lucene;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.BooleanClause.Occur;

public final class SearchRequest {

	private final String db;

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

		this.db = info.getString("db_name");
		this.skip = query.optInt("skip", 0);
		this.limit = query.optInt("limit", 25);
		this.debug = query.optBoolean("debug", false);
		this.include_docs = query.optBoolean("include_docs", false);

		// Parse query.
		final BooleanQuery q = new BooleanQuery();
		q.add(new TermQuery(new Term(Config.DB, this.db)), Occur.MUST);
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
		if (sort == null) {
			td=searcher.search(q, null, skip + limit);
		} else {
			td=searcher.search(q, null, skip + limit, sort);
		}

		final JSONObject result = new JSONObject();
		result.element("code", 200);
		
		final JSONObject json = new JSONObject();
		json.element("total_rows", td.totalHits);
		
		result.put("json", json);

		return result.toString();
	}

}
