package com.github.rnewson.couchdb.lucene.v2;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Allows purge and optimize calls.
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

    private LuceneHolder holder;

    AdminServlet(final LuceneHolder holder) {
        this.holder = holder;
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException,
            IOException {
        if ("/_expunge".equals(req.getPathInfo())) {
            holder.getIndexWriter().expungeDeletes(false);
            resp.setStatus(202);
            return;
        }

        if ("/_optimize".equals(req.getPathInfo())) {
            holder.getIndexWriter().optimize(false);
            resp.setStatus(202);
            return;
        }
        
        resp.sendError(400);
    }

}
