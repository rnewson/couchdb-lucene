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
import java.lang.reflect.InvocationTargetException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.ParseException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import com.github.rnewson.couchdb.lucene.couchdb.CouchDocument;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.couchdb.View;
import com.github.rnewson.couchdb.lucene.couchdb.ViewSettings;
import com.github.rnewson.couchdb.lucene.rhino.JSLog;
import com.github.rnewson.couchdb.lucene.rhino.RhinoDocument;

public final class DocumentConverter {

    private static final Document[] NO_DOCUMENTS = new Document[0];
    private static final Logger LOG = Logger.getLogger(DocumentConverter.class);

    private final Context context;
    private final Function viewFun;
    private final ScriptableObject scope;

    public DocumentConverter(final Context context, final View view) throws IOException {
        this.context = context;
        scope = context.initStandardObjects();

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
        viewFun = view.compileFunction(context, scope);
    }

    public Document[] convert(
            final CouchDocument doc,
            final ViewSettings defaults,
            final Database database) throws IOException, ParseException {
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
            return new Document[]{document};
        }

        if (result instanceof NativeArray) {
            final NativeArray nativeArray = (NativeArray) result;
            final Document[] arrayResult = new Document[(int) nativeArray.getLength()];
            for (int i = 0; i < (int) nativeArray.getLength(); i++) {
                if (nativeArray.get(i, null) instanceof RhinoDocument) {
                    final RhinoDocument rhinoDocument = (RhinoDocument) nativeArray.get(i, null);
                    final Document document = rhinoDocument.toDocument(
                            doc.getId(),
                            defaults,
                            database);
                    arrayResult[i] = document;
                }
            }
            return arrayResult;
        }

        return null;
    }

    private Object convert(final Object obj) {
        if (obj instanceof JSONArray) {
            return convertArray((JSONArray) obj);
        } else if (obj instanceof JSONObject) {
            return convertObject((JSONObject) obj);
        } else {
            return obj;
        }
    }

    private Scriptable convertArray(final JSONArray array) {
        final Scriptable result = context.newArray(scope, array.size());
        for (int i = 0, max = array.size(); i < max; i++) {
            ScriptableObject.putProperty(result, i, convert(array.get(i)));
        }
        return result;
    }

    private Scriptable convertObject(final JSONObject obj) {
        final Scriptable result = context.newObject(scope);
        for (final Object key : obj.keySet()) {
            final Object value = obj.get(key);
            ScriptableObject.putProperty(result, (String) key, convert(value));
        }
        return result;
    }

}
