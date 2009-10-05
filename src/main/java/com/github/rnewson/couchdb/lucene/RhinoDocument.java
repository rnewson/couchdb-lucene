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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.document.NumericField;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import com.github.rnewson.couchdb.lucene.util.Conversion;

public final class RhinoDocument extends ScriptableObject {

    private static final long serialVersionUID = 1L;

    static State state;

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

    public void add(final Fieldable field) {
        doc.add(field);
    }

    public static Scriptable jsConstructor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) {
        RhinoDocument doc = new RhinoDocument();
        if (args.length >= 2)
            jsFunction_add(cx, doc, args, ctorObj);
        return doc;
    }

    public static void jsFunction_add(final Context cx, final Scriptable thisObj, final Object[] args, final Function funObj) {
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

        String field = defaults.optString("field", Constants.DEFAULT_FIELD);
        String store = defaults.optString("store", "no");
        String index = defaults.optString("index", "analyzed");
        String type = defaults.optString("type", "string");

        // Check for local override.
        if (args.length == 2) {
            final NativeObject obj = (NativeObject) args[1];
            field = optString(obj, "field", field);
            store = optString(obj, "store", store);
            index = optString(obj, "index", index);
            type = optString(obj, "type", type);
        }

        final Field.Store storeObj = Store.get(store);
        Fieldable fieldObj = null;
        if ("int".equals(type)) {
            fieldObj = new NumericField(field, storeObj, true).setIntValue(Conversion.convert(args[0], Integer.class));
        } else if ("float".equals(type)) {
            fieldObj = new NumericField(field, storeObj, true).setFloatValue(Conversion.convert(args[0], Float.class));
        } else if ("double".equals(type)) {
            fieldObj = new NumericField(field, storeObj, true).setDoubleValue(Conversion.convert(args[0], Double.class));
        } else if ("long".equals(type)) {
            fieldObj = new NumericField(field, storeObj, true).setLongValue(Conversion.convert(args[0], Long.class));
        } else if ("date".equals(type)) {
            fieldObj = new NumericField(field, storeObj, true).setLongValue(Conversion.convert(args[0], Date.class).getTime());
        } else if ("string".equals(type)) {
            fieldObj = new Field(field, Conversion.convert(args[0]).toString(), storeObj, Index.get(index));
        } else {
            // Ignore.
        }
        if (fieldObj != null)
            doc.add(fieldObj);
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

    public static void jsFunction_attachment(final Context cx, final Scriptable thisObj, final Object[] args, final Function funObj)
            throws IOException {
        final RhinoDocument doc = checkInstance(thisObj);
        if (args.length < 2) {
            throw Context.reportRuntimeError("Invalid number of arguments.");
        }

        final String dbname = (String) cx.getThreadLocal("dbname");
        final String docid = (String) cx.getThreadLocal("docid");
        final String field = args[0].toString();
        final String attname = args[1].toString();
        final String url = state.couch.url(String.format("%s/%s/%s", dbname, Utils.urlEncode(docid), Utils.urlEncode(attname)));

        final HttpGet get = new HttpGet(url);

        final ResponseHandler<Void> responseHandler = new ResponseHandler<Void>() {

            public Void handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                final HttpEntity entity = response.getEntity();
                final InputStream in = entity.getContent();
                try {
                    TIKA.parse(in, entity.getContentType().getValue(), field, doc.doc);
                } finally {
                    in.close();
                }
                return null;
            }
        };

        state.httpClient.execute(get, responseHandler);
    }

    private static RhinoDocument checkInstance(Scriptable obj) {
        if (obj == null || !(obj instanceof RhinoDocument)) {
            throw Context.reportRuntimeError("called on incompatible object.");
        }
        return (RhinoDocument) obj;
    }
}
