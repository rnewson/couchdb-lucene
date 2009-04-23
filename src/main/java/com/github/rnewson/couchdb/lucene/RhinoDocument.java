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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public final class RhinoDocument extends ScriptableObject {

    private static final long serialVersionUID = 1L;

    private static final Database DB = new Database(Config.DB_URL);

    private static final Tika TIKA = new Tika();

    private static final DateFormat[] DATE_FORMATS = new DateFormat[] {
            new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z '('z')'"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'") };

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

        // defaults.
        String field = Config.DEFAULT_FIELD;
        String language = "en";
        Field.Store store = Field.Store.NO;
        Field.Index index = Field.Index.ANALYZED;
        Field.TermVector tv = Field.TermVector.NO;

        // Check for overrides.
        if (args.length == 2) {
            final NativeObject obj = (NativeObject) args[1];

            // Change the field name.
            if (obj.has("field", null)) {
                field = (String) obj.get("field", null);
            }
            
            // Change the stored flag.
            if (obj.has("store", null)) {
                store = Store.get(obj.get("store", null));
            }

            // Change the indexed flag.
            if (obj.has("index", null)) {
                index = Index.get(obj.get("index", null));
            }

            // Change the language.
            if (obj.has("language", null)) {
                language = (String) obj.get("language", null);
            }

        }

        doc.add(new Field(field, args[0].toString(), store, index, tv));
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

    public static void jsFunction_date(final Context cx, final Scriptable thisObj, final Object[] args,
            final Function funObj) throws IOException {
        final RhinoDocument doc = checkInstance(thisObj);
        if (args.length < 2) {
            throw Context.reportRuntimeError("field name and value required.");
        }

        final String field = args[0].toString();

        final Field.Store str;
        if (args.length > 2) {
            final String strtype = args[2].toString().toUpperCase();
            str = Store.get(strtype) == null ? Field.Store.NO : (Field.Store) Store.get(strtype);
        } else {
            str = Field.Store.NO;
        }

        // Is it a native date?
        try {
            final Date date = (Date) Context.jsToJava(args[1], Date.class);
            doc.doc.add(new Field(field, Long.toString(date.getTime()), str, Field.Index.NOT_ANALYZED_NO_NORMS));
            return;
        } catch (final EvaluatorException e) {
            // Ignore.
        }

        // Try to parse it as a string.
        final String value = Context.toString(args[1]);

        final DateFormat[] formats;
        if (args.length > 3) {
            formats = new DateFormat[] { new SimpleDateFormat(args[3].toString()) };
        } else {
            formats = DATE_FORMATS;
        }

        final Date parsed = parse_date(formats, value);
        if (parsed == null) {
            throw Context.reportRuntimeError("failed to parse date value: " + value);
        }

        doc.doc.add(new Field(field, Long.toString(parsed.getTime()), str, Field.Index.NOT_ANALYZED_NO_NORMS));
    }

    private static Date parse_date(final DateFormat[] formats, final String value) {
        for (final DateFormat fmt : formats) {
            try {
                return fmt.parse(value);
            } catch (final ParseException e) {
                continue;
            }
        }
        return null;
    }

    private static RhinoDocument checkInstance(Scriptable obj) {
        if (obj == null || !(obj instanceof RhinoDocument)) {
            throw Context.reportRuntimeError("called on incompatible object.");
        }
        return (RhinoDocument) obj;
    }
}
