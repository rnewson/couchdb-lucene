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

package com.github.rnewson.couchdb.lucene;

import com.github.rnewson.couchdb.lucene.couchdb.CouchDocument;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.couchdb.View;
import com.github.rnewson.couchdb.lucene.couchdb.ViewSettings;
import com.github.rnewson.couchdb.lucene.rhino.JSLog;
import com.github.rnewson.couchdb.lucene.rhino.RhinoDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public final class DocumentConverter {

    private static final Collection<Document> NO_DOCUMENTS = Collections.emptyList();
    private static final Logger LOG = LoggerFactory.getLogger(DocumentConverter.class);

    private final Context context;
    private final Function viewFun;
    private final ScriptableObject scope;

    public DocumentConverter(final Context context, final View view) throws IOException, JSONException {
        this.context = context;
        scope = context.initStandardObjects();
        context.setLanguageVersion(Context.VERSION_1_8);

        // Allow custom document helper class.
        try {
            ScriptableObject.defineClass(scope, RhinoDocument.class);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        // Add a log object
        ScriptableObject.putProperty(scope, "log", new JSLog());

        // Compile user-specified function
        try {
            viewFun = view.compileFunction(context, scope);
        } catch (final RhinoException e) {
            LOG.error("View code for " + view + " does not compile.");
            throw e;
        }
    }

    public Collection<Document> convert(
            final CouchDocument doc,
            final ViewSettings defaults,
            final Database database) throws IOException, ParseException, JSONException {
        final Object result;
        final Scriptable scriptable = convertObject(doc.asJson());

        try {
            result = viewFun.call(context, scope, null, new Object[]{scriptable});
        } catch (final JavaScriptException e) {
            LOG.warn(doc + " caused exception during conversion.", e);
            return NO_DOCUMENTS;
        }

        if (result == null || result instanceof Undefined) {
            return NO_DOCUMENTS;
        }

        if (result instanceof RhinoDocument) {
            final RhinoDocument rhinoDocument = (RhinoDocument) result;
            final Document document = rhinoDocument.toDocument(doc.getId(), defaults, database);
            return Collections.singleton(document);
        }

        if (result instanceof NativeArray) {
            final NativeArray nativeArray = (NativeArray) result;
            final Collection<Document> arrayResult = new ArrayList<>((int) nativeArray.getLength());
            for (int i = 0; i < (int) nativeArray.getLength(); i++) {
                if (nativeArray.get(i, null) instanceof RhinoDocument) {
                    final RhinoDocument rhinoDocument = (RhinoDocument) nativeArray.get(i, null);
                    final Document document = rhinoDocument.toDocument(
                            doc.getId(),
                            defaults,
                            database);
                    arrayResult.add(document);
                }
            }
            return arrayResult;
        }

        return null;
    }

    private Object convert(final Object obj) throws JSONException {
        if (obj instanceof JSONArray) {
            return convertArray((JSONArray) obj);
        } else if (obj == JSONObject.NULL) {
            return null;
        } else if (obj instanceof JSONObject) {
            return convertObject((JSONObject) obj);
        } else {
            return obj;
        }
    }

    private Scriptable convertArray(final JSONArray array) throws JSONException {
        final Scriptable result = context.newArray(scope, array.length());
        for (int i = 0, max = array.length(); i < max; i++) {
            ScriptableObject.putProperty(result, i, convert(array.get(i)));
        }
        return result;
    }

    private Scriptable convertObject(final JSONObject obj) throws JSONException {
        if (obj == JSONObject.NULL) {
            return null;
        }
        final Scriptable result = context.newObject(scope);
        final Iterator<?> it = obj.keys();
        while (it.hasNext()) {
            final String key = (String) it.next();
            final Object value = obj.get(key);
            ScriptableObject.putProperty(result, key, convert(value));
        }
        return result;
    }

}
