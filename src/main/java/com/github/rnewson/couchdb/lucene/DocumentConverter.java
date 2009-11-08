package com.github.rnewson.couchdb.lucene;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.document.Document;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import com.github.rnewson.couchdb.lucene.RhinoDocument.RhinoContext;

public final class DocumentConverter {

    private static final Document[] NO_DOCUMENTS = new Document[0];

    private final Context context;
    private final Function main;
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

        // Load JSON parser.
        context.evaluateString(scope, loadResource("json2.js"), "json2", 0, null);

        // Define outer function.
        main = context.compileFunction(scope, "function(json, func) { return func(JSON.parse(json)); }", "main", 0, null);

        // Compile user-specified function
        viewFun = context.compileFunction(scope, function, functionName, 0, null);
    }

    public Document[] convert(final JSONObject doc, final RhinoContext rhinoContext) throws IOException {
        final Object result = main.call(context, scope, null, new Object[] { doc.toString(), viewFun });

        if (result == null || result instanceof Undefined) {
            return NO_DOCUMENTS;
        }

        if (result instanceof RhinoDocument) {
            return new Document[] { ((RhinoDocument) result).toDocument(rhinoContext) };
        }

        if (result instanceof NativeArray) {
            final NativeArray array = (NativeArray) result;
            final Document[] result2 = new Document[(int) array.getLength()];
            for (int i = 0; i < (int) array.getLength(); i++) {
                if (array.get(i, null) instanceof RhinoDocument) {
                    result2[i] = ((RhinoDocument) result).toDocument(rhinoContext);
                }
            }
            return result2;
        }

        return null;
    }

    private String loadResource(final String name) throws IOException {
        final InputStream in = Indexer.class.getClassLoader().getResourceAsStream(name);
        try {
            return IOUtils.toString(in, "UTF-8");
        } finally {
            in.close();
        }
    }

}
