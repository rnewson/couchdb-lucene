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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.store.Directory;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public final class Utils {

    private Utils() {
        throw new InstantiationError("This class is not supposed to be instantiated.");
    }

    public static Logger getLogger(final Class<?> clazz, final String suffix) {
        return LoggerFactory.getLogger(clazz.getCanonicalName() + "." + suffix);
    }

    public static boolean getStaleOk(final HttpServletRequest req) {
        return "ok".equals(req.getParameter("stale"));
    }

    public static Field text(final String name, final String value, final boolean store) {
        return new TextField(name, value, store ? Store.YES : Store.NO);
    }

    public static Field token(final String name, final String value, final boolean store) {
        return new StringField(name, value, store ? Store.YES : Store.NO);
    }

    public static String urlEncode(final String path) {
        try {
            return URLEncoder.encode(path, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            throw new Error("UTF-8 support missing!");
        }
    }

    public static long directorySize(final Directory dir) throws IOException {
        long result = 0;
        for (final String name : dir.listAll()) {
            result += dir.fileLength(name);
        }
        return result;
    }

    /**
     * Split a string on commas but respect commas inside quotes.
     *
     * @param str
     * @return
     */
    public static String[] splitOnCommas(final String str) {
        return str.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
    }

}
