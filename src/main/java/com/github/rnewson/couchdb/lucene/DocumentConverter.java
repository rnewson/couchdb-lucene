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

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.rhino.JSLog;
import com.github.rnewson.couchdb.rhino.JsonToRhinoConverter;
import com.github.rnewson.couchdb.rhino.RhinoDocument;

public final class DocumentConverter {

    private static final Document[] NO_DOCUMENTS = new Document[0];
    private static final Logger LOG = Logger.getLogger(DocumentConverter.class);

    private final Context context;
    private final Function viewFun;
    private final ScriptableObject scope;

    public DocumentConverter(final Context context, final String functionName, final String function) throws IOException {
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
        viewFun = context.compileFunction(scope, trim(function), functionName, 0, null);
    }

    public Document[] convert(final JSONObject doc, final JSONObject defaults, final Database database) throws IOException {
        final Object result;
        final ScriptableObject scriptableObject = JsonToRhinoConverter.convertObject(doc);

        try {
            result = viewFun.call(context, scope, null, new Object[] { scriptableObject });
        } catch (final JavaScriptException e) {
            LOG.warn(doc + " caused exception during conversion.", e);
            return NO_DOCUMENTS;
        }

        if (result == null || result instanceof Undefined) {
            return NO_DOCUMENTS;
        }

        if (result instanceof RhinoDocument) {
            final RhinoDocument rhinoDocument = (RhinoDocument) result;
            final Document document = rhinoDocument.toDocument(doc.getString("_id"), defaults, database);
            return new Document[] { document };
        }

        if (result instanceof NativeArray) {
            final NativeArray nativeArray = (NativeArray) result;
            final Document[] arrayResult = new Document[(int) nativeArray.getLength()];
            for (int i = 0; i < (int) nativeArray.getLength(); i++) {
                if (nativeArray.get(i, null) instanceof RhinoDocument) {
                    final RhinoDocument rhinoDocument = (RhinoDocument) nativeArray.get(i, null);
                    final Document document = rhinoDocument.toDocument(doc.getString("_id"), defaults, database);
                    arrayResult[i] = document;
                }
            }
            return arrayResult;
        }

        return null;
    }

    private String trim(final String fun) {
        String result = fun;
        result = StringUtils.trim(result);
        result = StringUtils.removeStart(result, "\"");
        result = StringUtils.removeEnd(result, "\"");
        return result;
    }

}
