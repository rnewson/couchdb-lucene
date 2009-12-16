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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.store.Directory;

public class Utils {

    public static Logger getLogger(final Class<?> clazz, final String suffix) {
        return Logger.getLogger(clazz.getCanonicalName() + "." + suffix);
    }

    public static void setResponseContentTypeAndEncoding(final HttpServletRequest req, final HttpServletResponse resp) {
        final String accept = req.getHeader("Accept");
        if (getBooleanParameter(req, "force_json") || (accept != null && accept.contains("application/json"))) {
            resp.setContentType("application/json");
        } else {
            resp.setContentType("text/plain");
        }
        resp.setCharacterEncoding("utf-8");
    }

    public static boolean getStaleOk(final HttpServletRequest req) {
        return "ok".equals(req.getParameter("stale"));
    }

    public static Field text(final String name, final String value, final boolean store) {
        return new Field(name, value, store ? Store.YES : Store.NO, Field.Index.ANALYZED);
    }

    public static Field token(final String name, final String value, final boolean store) {
        return new Field(name, value, store ? Store.YES : Store.NO, Field.Index.NOT_ANALYZED_NO_NORMS);
    }

    public static String urlEncode(final String path) {
        try {
            return URLEncoder.encode(path, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            throw new Error("UTF-8 support missing!");
        }
    }

    public static String getHost(final String path) {
        return split(path, true)[0];
    }

    public static int getPort(final String path) {
        return Integer.parseInt(split(path, true)[1]);
    }

    public static String getDatabase(final String path) {
        return split(path, true)[2];
    }

    public static String getDesignDocumentName(final String path) {
        return split(path, true)[3];
    }

    public static String getViewName(final String path) {
        return split(path, true)[4];
    }

    public static boolean validatePath(final String path) {
        return split(path, false).length == 5;
    }
   
    private static String[] split(final String path, final boolean throwIfWrong) {
        final String[] result = path.substring(1).split("/");
        if (throwIfWrong && result.length != 5) {
            throw new IllegalArgumentException("Malformed path (" + Arrays.toString(result) + ")");
        }
        return result;
    }
    
    public static String getPath(final HttpServletRequest req) {
        return req.getPathInfo();
    }

    public static long directorySize(final Directory dir) throws IOException {
        long result = 0;
        for (final String name : dir.listAll()) {
            result += dir.fileLength(name);
        }
        return result;
    }

}
