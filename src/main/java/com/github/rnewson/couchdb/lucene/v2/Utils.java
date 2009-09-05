package com.github.rnewson.couchdb.lucene.v2;

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

import net.sf.json.JSONArray;

class Utils {

    /*
    public static final Logger LOG = Logger.getLogger("couchdb-lucene");

    private static PrintWriter OUT;

    static {
        try {
            OUT = new PrintWriter(new OutputStreamWriter(System.out, "UTF-8"));
        } catch (final UnsupportedEncodingException e) {
            throw new Error("UTF-8 support is missing from your JVM.");
        }
    }

    public static void out(final Object obj) {
        if (OUT == null) {
            try {
                OUT = new PrintWriter(new OutputStreamWriter(System.out, "UTF-8"));
            } catch (final UnsupportedEncodingException e) {
                throw new Error("UTF-8 support is missing from your JVM.");
            }
        }
        OUT.println(obj.toString());
        OUT.flush();
    }

    public static String throwableToJSON(final Throwable t) {
        return error(t.getMessage() == null ? "Unknown error" : String.format("%s: %s", t.getClass(), t.getMessage()));
    }

    public static String error(final String txt) {
        return error(500, txt);
    }

    public static String digest(final String data) {
        return DigestUtils.md5Hex(data);
    }

    public static String error(final int code, final Throwable t) {
        final StringWriter writer = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(writer);
        if (t.getMessage() != null) {
            printWriter.append(t.getMessage());
            printWriter.append("\n");
        }
        t.printStackTrace(printWriter);
        return new JSONObject().element("code", code).element("body", "<pre>" + writer + "</pre>").toString();
    }

    public static String error(final int code, final String txt) {
        return new JSONObject().element("code", code).element("body", StringEscapeUtils.escapeHtml(txt)).toString();
    }

    public static Field text(final String name, final String value, final boolean store) {
        return new Field(name, value, store ? Store.YES : Store.NO, Field.Index.ANALYZED);
    }

    public static Field token(final String name, final String value, final boolean store) {
        return new Field(name, value, store ? Store.YES : Store.NO, Field.Index.NOT_ANALYZED_NO_NORMS);
    }

    public static Query docQuery(final String viewname, final String id) {
        BooleanQuery q = new BooleanQuery();
        q.add(new TermQuery(new Term(Constants.VIEW, viewname)), Occur.MUST);
        q.add(new TermQuery(new Term(Constants.ID, id)), Occur.MUST);
        return q;
    }
    */

    public static String viewname(final JSONArray path) {
        return String.format("%s/%s/%s", path.getString(0), path.getString(2), path.getString(3));
    }

}
