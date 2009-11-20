package com.github.rnewson.couchdb.lucene.util;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.jetty.HttpHeaders;

import net.sf.json.JSONObject;

public final class ServletUtils {

    public static boolean getBooleanParameter(final HttpServletRequest req, final String parameterName) {
        return Boolean.parseBoolean(req.getParameter(parameterName));
    }

    public static int getIntParameter(final HttpServletRequest req, final String parameterName, final int defaultValue) {
        final String result = req.getParameter(parameterName);
        return result != null ? Integer.parseInt(result) : defaultValue;
    }

    public static long getLongParameter(final HttpServletRequest req, final String parameterName, final long defaultValue) {
        final String result = req.getParameter(parameterName);
        return result != null ? Long.parseLong(result) : defaultValue;
    }

    public static String getParameter(final HttpServletRequest req, final String parameterName, final String defaultValue) {
        final String result = req.getParameter(parameterName);
        return result != null ? result : defaultValue;
    }

    public static void sendJSONError(final HttpServletRequest request, final HttpServletResponse response, final int code,
            final String reason) throws IOException {
        final JSONObject obj = new JSONObject();
        obj.put("code", code);
        obj.put("reason", reason);

        Utils.setResponseContentTypeAndEncoding(request, response);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
        response.setStatus(code);
        
        final Writer writer = response.getWriter();
        try {
            writer.write(obj.toString());
        } finally {
            writer.close();
        }
    }

}
