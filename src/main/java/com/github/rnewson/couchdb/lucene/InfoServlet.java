package com.github.rnewson.couchdb.lucene;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.store.Directory;

import com.github.rnewson.couchdb.lucene.LuceneGateway.ReaderCallback;

/**
 * Provides information current indexes.
 * 
 * @author robertnewson
 * 
 */
public class InfoServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final State state;

    InfoServlet(final State state) {
        this.state = state;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        if (req.getParameter("index") == null) {
            resp.sendError(400, "Missing index attribute.");
            return;
        }

        final ViewSignature sig = state.locator.lookup(req);
        if (sig == null) {
            resp.sendError(400, "Invalid path.");
            return;
        }

        final boolean staleOk = "ok".equals(req.getParameter("stale"));
        final JSONObject json = state.lucene.withReader(sig, staleOk, new ReaderCallback<JSONObject>() {
            @Override
            public JSONObject callback(final IndexReader reader) throws IOException {
                final JSONObject result = new JSONObject();
                result.put("current", reader.isCurrent());
                result.put("disk_size", size(reader.directory()));
                result.put("doc_count", reader.numDocs());
                result.put("doc_del_count", reader.numDeletedDocs());
                final JSONArray fields = new JSONArray();
                for (final Object field : reader.getFieldNames(FieldOption.INDEXED)) {
                    if (((String) field).startsWith("_"))
                        continue;
                    fields.add(field);
                }
                result.put("fields", fields);
                result.put("last_modified", IndexReader.lastModified(reader.directory()));
                result.put("optimized", reader.isOptimized());
                result.put("ref_count", reader.getRefCount());

                final JSONObject info = new JSONObject();
                info.put("code", 200);
                info.put("json", result);
                return result;
            }
        });

        resp.setContentType(Constants.CONTENT_TYPE);
        final Writer writer = resp.getWriter();
        try {
            writer.write(json.toString());
        } finally {
            writer.close();
        }
    }

    private static long size(final Directory dir) throws IOException {
        long result = 0;
        for (final String name : dir.listAll()) {
            result += dir.fileLength(name);
        }
        return result;
    }

}
