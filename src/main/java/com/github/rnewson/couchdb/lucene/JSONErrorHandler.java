package com.github.rnewson.couchdb.lucene;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.handler.ErrorHandler;

import com.github.rnewson.couchdb.lucene.util.ServletUtils;

/**
 * Convert errors to CouchDB-style JSON objects.
 * 
 * @author robertnewson
 * 
 */
public final class JSONErrorHandler extends ErrorHandler {

    public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException {
        HttpConnection connection = HttpConnection.getCurrentConnection();
        connection.getRequest().setHandled(true);
        ServletUtils.sendJSONError(request, response, connection.getResponse().getStatus(), connection.getResponse().getReason());
    }

}
