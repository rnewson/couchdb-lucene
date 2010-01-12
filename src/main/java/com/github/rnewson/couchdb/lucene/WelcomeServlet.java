package com.github.rnewson.couchdb.lucene;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;

import com.github.rnewson.couchdb.lucene.util.Utils;

public final class WelcomeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        Utils.setResponseContentTypeAndEncoding(req, resp);
        final JSONObject welcome = new JSONObject();
        welcome.put("couchdb-lucene", "Welcome");
        welcome.put("version", "0.5.0");
        Utils.writeJSON(resp, welcome);
    }

}
