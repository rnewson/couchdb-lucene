package com.github.rnewson.couchdb.lucene;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;

import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.HttpHeaders;
import org.mortbay.jetty.handler.ErrorHandler;

public final class JSONErrorHandler extends ErrorHandler {

    public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException {
        HttpConnection connection = HttpConnection.getCurrentConnection();
        connection.getRequest().setHandled(true);
        response.setContentType("application/json; charset=utf-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
        final JSONObject obj = new JSONObject();
        obj.put("code", connection.getResponse().getStatus());
        obj.put("reason", connection.getResponse().getReason());

        final byte[] body = obj.toString().getBytes("UTF-8");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

}
