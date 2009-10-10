package com.github.rnewson.couchdb.lucene.util;

import javax.servlet.http.HttpServletRequest;

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

}
