package com.github.rnewson.couchdb.lucene;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.configuration.Configuration;
import org.apache.lucene.index.IndexWriter;

import com.github.rnewson.couchdb.lucene.Lucene.WriterCallback;
import com.github.rnewson.couchdb.lucene.util.IndexPath;
import com.github.rnewson.couchdb.lucene.util.ServletUtils;

/**
 * Administrative functions.
 * 
 * <ul>
 * <li>_expunge
 * <li>_optimize
 * <li>_pause
 * <li>_resume
 * </ul>
 * 
 * @author rnewson
 * 
 */
public final class AdminServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private Lucene lucene;

    private Configuration configuration;

    public void setLucene(final Lucene lucene) {
        this.lucene = lucene;
    }

    public void setConfiguration(final Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final String command = req.getParameter("cmd");

        final IndexPath path = IndexPath.parse(req);

        if (path == null) {
            ServletUtils.sendJSONError(req, resp, 400, "Bad path");
            return;
        }

        if ("expunge".equals(command)) {
            lucene.withWriter(path, new WriterCallback() {
                public boolean callback(final IndexWriter writer) throws IOException {
                    writer.expungeDeletes(false);
                    return false;
                }

                public void onMissing() throws IOException {
                    resp.sendError(404);
                }
            });
            resp.setStatus(202);
            return;
        }

        if ("optimize".equals(command)) {
            lucene.withWriter(path, new WriterCallback() {
                public boolean callback(final IndexWriter writer) throws IOException {
                    writer.optimize(false);
                    return false;
                }

                public void onMissing() throws IOException {
                    resp.sendError(404);
                }
            });
            resp.setStatus(202);
            return;
        }

        resp.sendError(400, "Bad request");
    }

}
