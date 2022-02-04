/*
 * Copyright Robert Newson
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

package com.github.rnewson.couchdb.lucene.util;

import org.json.JSONException;
import org.json.JSONObject;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

public final class ServletUtils {

    private ServletUtils() {
        throw new InstantiationError("This class is not supposed to be instantiated.");
    }

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

    public static void sendJsonError(final HttpServletRequest request, final HttpServletResponse response, final int code,
                                     final String reason) throws IOException, JSONException {
        final JSONObject obj = new JSONObject();
        obj.put("reason", reason);
        sendJsonError(request, response, code, obj);
    }

    public static void sendJsonError(final HttpServletRequest request, final HttpServletResponse response, final int code,
                                     final JSONObject error) throws IOException, JSONException {
        setResponseContentTypeAndEncoding(request, response);
        response.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
        response.setStatus(code);
        error.put("code", code);

        final Writer writer = response.getWriter();
        try {
            writer.write(error.toString());
            writer.write("\r\n");
        } finally {
            writer.close();
        }
    }

    public static void sendJson(final HttpServletRequest req, final HttpServletResponse resp, final JSONObject json) throws IOException {
        setResponseContentTypeAndEncoding(req, resp);
        final Writer writer = resp.getWriter();
        try {
            writer.write(json.toString() + "\r\n");
        } finally {
            writer.close();
        }
    }

    public static void sendJsonSuccess(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        setResponseContentTypeAndEncoding(req, resp);
        final Writer writer = resp.getWriter();
        try {
            writer.write("{\"ok\": true}\r\n");
        } finally {
            writer.close();
        }
    }

}
