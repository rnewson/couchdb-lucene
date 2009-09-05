package com.github.rnewson.couchdb.lucene.v2;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class IndexingServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final LuceneHolder holder;

    IndexingServlet(final LuceneHolder holder) throws IOException {
        this.holder = holder;
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException,
            IOException {
        super.doPost(req, resp);
    }

}
