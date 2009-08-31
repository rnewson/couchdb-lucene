package com.github.rnewson.couchdb.lucene;

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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import net.sf.json.JSONObject;

import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public final class RhinoDocument extends ScriptableObject {

    private static final long serialVersionUID = 1L;

    private static final Database DB = new Database(Config.DB_URL);

    private static final Tika TIKA = new Tika();

    private static final Map<String, Field.Index> Index = new HashMap<String, Field.Index>();

    private static final Map<String, Field.Store> Store = new HashMap<String, Field.Store>();

    static {
        Store.put("no", Field.Store.NO);
        Store.put("yes", Field.Store.YES);

        Index.put("analyzed", Field.Index.ANALYZED);
        Index.put("analyzed_no_norms", Field.Index.ANALYZED_NO_NORMS);
        Index.put("no", Field.Index.NO);
        Index.put("not_analyzed", Field.Index.NOT_ANALYZED);
        Index.put("not_analyzed_no_norms", Field.Index.NOT_ANALYZED_NO_NORMS);
    }

    final Document doc;

    public RhinoDocument() {
        doc = new Document();
    }

    public String getClassName() {
        return "Document";
    }

    public void add(final Field field) {
        doc.add(field);
    }

    public static Scriptable jsConstructor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) {
        RhinoDocument doc = new RhinoDocument();
        if (args.length >= 2)
            jsFunction_add(cx, doc, args, ctorObj);
        return doc;
    }

    public static void jsFunction_add(final Context cx, final Scriptable thisObj, final Object[] args,
            final Function funObj) {
        final RhinoDocument doc = checkInstance(thisObj);

        if (args.length < 1 || args.length > 2) {
            throw Context.reportRuntimeError("Invalid number of arguments.");
        }

        if (args[0] == null) {
            throw Context.reportRuntimeError("first argument must be non-null.");
        }

        if (args.length == 2 && (args[1] == null || args[1] instanceof NativeObject == false)) {
            throw Context.reportRuntimeError("second argument must be an object.");
        }

        final JSONObject defaults = JSONObject.fromObject((String) cx.getThreadLocal("defaults"));

        String language = defaults.optString("language", "en");
        String field = defaults.optString("field", Config.DEFAULT_FIELD);
        String store = defaults.optString("store", "no");
        String index = defaults.optString("index", "analyzed");

        // Check for local override.
        if (args.length == 2) {
            final NativeObject obj = (NativeObject) args[1];
            language = optString(obj, "language", language);
            field = optString(obj, "field", field);
            store = optString(obj, "store", store);
            index = optString(obj, "index", index);
        }

        final Object obj = Conversion.convert(args[0]);
        System.err.println(obj.getClass());
        if (obj instanceof Date) {
            // Special indexed form.
            doc.add(new Field(field, Long.toString(((Date) obj).getTime()), Field.Store.NO,
                    Field.Index.NOT_ANALYZED_NO_NORMS));

            // Store in ISO8601 format, if requested.
            if ("yes".equals(store)) {
                final String asString = DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT.format(obj);
                doc.add(new Field(field, asString, Field.Store.YES, Field.Index.NO));
            }
        } else {
            doc.add(new Field(field, obj.toString(), Store.get(store), Index.get(index)));
        }
    }

    /**
     * Foreign method for NativeObject.
     */
    private static String optString(final NativeObject obj, final String key, final String defaultValue) {
        if (obj.has(key, null)) {
            final Object value = obj.get(key, null);
            return value instanceof String ? (String) value : defaultValue;
        }
        return defaultValue;
    }

    public static void jsFunction_attachment(final Context cx, final Scriptable thisObj, final Object[] args,
            final Function funObj) throws IOException {
        final RhinoDocument doc = checkInstance(thisObj);
        if (args.length < 2) {
            throw Context.reportRuntimeError("Invalid number of arguments.");
        }

        final String dbname = (String) cx.getThreadLocal("dbname");
        final String docid = (String) cx.getThreadLocal("docid");
        final String field = args[0].toString();
        final String attname = args[1].toString();
        final String url = DB.url(String.format("%s/%s/%s", dbname, DB.encode(docid), DB.encode(attname)));

        final GetMethod get = new GetMethod(url);
        try {
            final int sc = Database.CLIENT.executeMethod(get);
            if (sc == 200) {
                final String ctype = get.getResponseHeader("content-type").getValue();
                final InputStream in = get.getResponseBodyAsStream();
                try {
                    TIKA.parse(in, ctype, field, doc.doc);
                } finally {
                    in.close();
                }
            } else {
                throw Context.reportRuntimeError("failed to retrieve attachment: " + sc);
            }
        } finally {
            get.releaseConnection();
        }
    }

    private static RhinoDocument checkInstance(Scriptable obj) {
        if (obj == null || !(obj instanceof RhinoDocument)) {
            throw Context.reportRuntimeError("called on incompatible object.");
        }
        return (RhinoDocument) obj;
    }
}
