package com.github.rnewson.couchdb.lucene.v2;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.IndexSearcher;

public final class SearchServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(SearchServlet.class);

    private final LuceneHolder holder;

    SearchServlet(final LuceneHolder holder) throws IOException {
        this.holder = holder;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException,
            IOException {
        final JSONObject json = toJSON(req);

        if (!json.has("query")) {
            resp.sendError(400, "Missing query attribute.");
            return;
        }

        final JSONObject query = json.getJSONObject("query");

        if (!query.has("q")) {
            resp.sendError(400, "Missing q attribute.");
            return;
        }

        // Refresh reader and searcher unless stale=ok.
        if (!"ok".equals(query.optString("stale", null))) {
            holder.reopenReader();
        }

        final JSONArray path = json.getJSONArray("path");

        if (path.size() < 3) {
            resp.sendError(400, "No design document in path.");
            return;
        }

        if (path.size() < 4) {
            resp.sendError(400, "No view name in path.");
            return;
        }

        if (path.size() > 4) {
            resp.sendError(400, "Extra path info in request.");
            return;
        }

        // final String viewname = Utils.viewname(path);
        assert path.size() == 4;

        final SearchRequest request;
        try {
            request = new SearchRequest(json);
        } catch (final ParseException e) {
            resp.sendError(400, "Failed to parse query.");
            return;
        }

        resp.setContentType(Constants.CONTENT_TYPE);
        final Writer writer = resp.getWriter();
        try {
            final IndexSearcher searcher = holder.borrowSearcher();
            try {
                writer.write(request.execute(searcher));
            } finally {
                holder.returnSearcher(searcher);
            }
        } finally {
            writer.close();
        }
    }

    private JSONObject toJSON(final HttpServletRequest req) throws IOException {
        final Reader reader = req.getReader();
        try {
            return JSONObject.fromObject(IOUtils.toString(reader));
        } finally {
            reader.close();
        }
    }

}
