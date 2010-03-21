package com.github.rnewson.couchdb.lucene.util;

/**
 * Copyright 2009 Robert Newson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static com.github.rnewson.couchdb.lucene.util.ServletUtils.getBooleanParameter;

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

        setResponseContentTypeAndEncoding(request, response);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
        response.setStatus(code);
        
        final Writer writer = response.getWriter();
        try {
            writer.write(obj.toString());
        } finally {
            writer.close();
        }
    }

	public static void setResponseContentTypeAndEncoding(final HttpServletRequest req, final HttpServletResponse resp) {
	    final String accept = req.getHeader("Accept");
	    if (getBooleanParameter(req, "force_json") || (accept != null && accept.contains("application/json"))) {
	        resp.setContentType("application/json");
	    } else {
	        resp.setContentType("text/plain");
	    }
	    if (!resp.containsHeader("Vary")) {
	    	resp.addHeader("Vary", "Accept");
	    }
	    resp.setCharacterEncoding("utf-8");
	}

	public static void writeJSON(final HttpServletResponse resp, final JSONObject json) throws IOException {
	    final Writer writer = resp.getWriter();
	    try {
	        writer.write(json.toString() + "\r\n");
	    } finally {
	        writer.close();
	    }
	}

}
