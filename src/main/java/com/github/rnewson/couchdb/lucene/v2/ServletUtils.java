package com.github.rnewson.couchdb.lucene.v2;

import javax.servlet.http.HttpServletRequest;

final class ServletUtils {

    static boolean getBooleanParameter(final HttpServletRequest req, final String parameterName) {
        return Boolean.parseBoolean(req.getParameter(parameterName));
    }

    static String getParameter(final HttpServletRequest req, final String parameterName, final String defaultValue) {
        final String result = req.getParameter(parameterName);
        return result != null ? result : defaultValue;
    }
    
    static int getIntParameter(final HttpServletRequest req, final String parameterName, final int defaultValue) {
        final String result = req.getParameter(parameterName);
        return result != null ? Integer.parseInt(result) : defaultValue;        
    }

    static long getLongParameter(final HttpServletRequest req, final String parameterName, final long defaultValue) {
        final String result = req.getParameter(parameterName);
        return result != null ? Long.parseLong(result) : defaultValue;        
    }
    
}
