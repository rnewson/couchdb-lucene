package com.github.rnewson.couchdb.lucene;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.lucene.index.IndexWriter;

import com.github.rnewson.couchdb.lucene.LuceneGateway.WriterCallback;

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

    private Locator locator;
    private LuceneGateway lucene;

    public void setLocator(final Locator locator) {
        this.locator = locator;
    }

    public void setLucene(final LuceneGateway lucene) {
        this.lucene = lucene;
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final ViewSignature sig = locator.lookup(req);
        if (sig == null) {
            resp.sendError(400, "Invalid path.");
            return;
        }

        final String command = req.getParameter("cmd");

        if ("expunge".equals(command)) {
            lucene.withWriter(sig, new WriterCallback() {
                public boolean callback(final IndexWriter writer) throws IOException {
                    writer.expungeDeletes(false);
                    return false;
                }
            });
            resp.setStatus(202);
            return;
        }

        if ("optimize".equals(command)) {
            lucene.withWriter(sig, new WriterCallback() {
                public boolean callback(final IndexWriter writer) throws IOException {
                    writer.optimize(false);
                    return false;
                }
            });
            resp.setStatus(202);
            return;
        }

        resp.sendError(400, "Bad request");
    }

}
