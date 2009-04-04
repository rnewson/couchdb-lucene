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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class RhinoDocument extends ScriptableObject {

    public final Document doc;

    private static final Database DB = new Database(Config.DB_URL);

    private static final Tika TIKA = new Tika();

    private static final DateFormat[] DATE_FORMATS = new DateFormat[] {
            new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z '('z')'"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'") };

    private static final Map Index = new HashMap();

    private static final Map Store = new HashMap();

    private static final Map TermVector = new HashMap();

    static {
        Store.put("NO", Field.Store.NO);
        Store.put("YES", Field.Store.YES);

        Index.put("ANALYZED", Field.Index.ANALYZED);
        Index.put("ANALYZED_NO_NORMS", Field.Index.ANALYZED_NO_NORMS);
        Index.put("NO", Field.Index.NO);
        Index.put("NOT_ANALYZED", Field.Index.NOT_ANALYZED);
        Index.put("NOT_ANALYZED_NO_NORMS", Field.Index.NOT_ANALYZED_NO_NORMS);

        TermVector.put("NO", Field.TermVector.NO);
        TermVector.put("WITH_OFFSETS", Field.TermVector.WITH_OFFSETS);
        TermVector.put("WITH_POSITIONS", Field.TermVector.WITH_POSITIONS);
        TermVector.put("WITH_POSITIONS_OFFSETS", Field.TermVector.WITH_POSITIONS_OFFSETS);
        TermVector.put("YES", Field.TermVector.YES);
    }

    public RhinoDocument() {
        doc = new Document();
    }

    public String getClassName() {
        return "Document";
    }

    public static Scriptable jsConstructor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) {
        RhinoDocument doc = new RhinoDocument();
        if (args.length >= 2)
            jsFunction_field(cx, doc, args, ctorObj);
        return doc;
    }

    public static void jsFunction_field(final Context cx, final Scriptable thisObj, final Object[] args,
            final Function funObj) {
        final RhinoDocument doc = checkInstance(thisObj);
        if (args.length < 2) {
            throw Context.reportRuntimeError("Invalid number of arguments.");
        }

        Field.Store str = null;
        Field.Index idx = null;
        Field.TermVector tv = null;

        if (args.length >= 3) {
            str = (Field.Store) Store.get(args[2].toString().toUpperCase());
        }
        if (str == null)
            str = Field.Store.NO;

        if (args.length >= 4) {
            idx = (Field.Index) Index.get(args[3].toString().toUpperCase());
        }
        if (idx == null)
            idx = Field.Index.ANALYZED;

        if (args.length >= 5) {
            tv = (Field.TermVector) TermVector.get(args[4].toString().toUpperCase());
        }
        if (tv == null)
            tv = Field.TermVector.NO;

        doc.doc.add(new Field(args[0].toString(), args[1].toString(), str, idx, tv));
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
        System.err.println("ATTACHMENT: " + url);
        final GetMethod get = new GetMethod(url);
        try {
            final int sc = Database.CLIENT.executeMethod(get);
            if (sc == 200) {
                final String ctype = get.getResponseHeader("content-type").toString();
                TIKA.parse(get.getResponseBodyAsStream(), ctype, field, doc.doc);
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
        final String value = args[1].toString();

        final Field.Store str;
        if (args.length > 2) {
            final String strtype = args[2].toString().toUpperCase();
            str = Store.get(strtype) == null ? Field.Store.NO : (Field.Store) Store.get(strtype);
        } else {
            str = Field.Store.NO;
        }

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
