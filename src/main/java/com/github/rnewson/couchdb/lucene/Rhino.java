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

import org.apache.commons.io.IOUtils;
import org.apache.lucene.document.Document;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

public final class Rhino {

    private static final ClassShutter SHUTTER = new ClassShutter() {

        public boolean visibleToScripts(String fullClassName) {
            return false;
        }

    };

    private static final Document[] NO_DOCUMENTS = new Document[0];

    private static final ContextFactory contextFactory = new ContextFactory();

    private final Context context;

    private final Scriptable scope;

    private final Function userFun;

    private final Function systemFun;

    private final String fun;

    public Rhino(final String dbname, final String fun) throws Exception {
        this(dbname, "{}", fun);
    }

    public Rhino(final String dbname, final String defaults, final String fun) throws Exception {
        assert defaults != null;

        this.fun = fun;
        this.context = contextFactory.enterContext();
        try {
            this.context.setClassShutter(SHUTTER);
        } catch (final SecurityException e) {
            // Thrown if already set and Rhino reassociates a previous context
            // with this thread.
        }

        // Stash some context.
        this.context.putThreadLocal("dbname", dbname);
        this.context.putThreadLocal("defaults", defaults);

        context.setOptimizationLevel(9);
        scope = context.initStandardObjects();

        // compile user-defined function.
        this.userFun = context.compileFunction(scope, fun, "userFun", 0, null);

        // compile system function.
        this.systemFun = context.compileFunction(scope,
                "function(json, func) { var doc=JSON.parse(json); return func(doc); }", "systemFun", 0, null);

        ScriptableObject.defineClass(scope, RhinoDocument.class);

        // add JSON parser.
        context.evaluateString(scope, loadResource("json2.js"), "json2", 0, null);
    }

    private static String loadResource(final String name) throws IOException {
        final InputStream in = Rhino.class.getClassLoader().getResourceAsStream(name);
        try {
            return IOUtils.toString(in, "UTF-8");
        } finally {
            in.close();
        }
    }

    public Document[] map(final String docid, final String doc) {
        context.putThreadLocal("docid", docid);
        Object ret;
        try {
            ret = systemFun.call(context, scope, null, new Object[] { doc, userFun });
        } catch (final RhinoException e) {
            Utils.LOG.warn("function raised exception (" + e.getMessage() + ") with " + docid, e);
            return NO_DOCUMENTS;
        }
        if (ret == null || ret instanceof Undefined) {
            return NO_DOCUMENTS;
        } else if (ret instanceof RhinoDocument) {
            return new Document[] { ((RhinoDocument) ret).doc };
        } else if (ret instanceof NativeArray) {
            final NativeArray na = (NativeArray) ret;
            final Document[] mapped = new Document[(int) na.getLength()];
            for (int i = 0; i < (int) na.getLength(); i++) {
                ret = na.get(i, null);
                if (!(ret instanceof RhinoDocument)) {
                    throw new RuntimeException("Invalid object type: " + ret.getClass().getName());
                }
                mapped[i] = ((RhinoDocument) ret).doc;
            }
            return mapped;
        }

        throw new RuntimeException("Invalid object type: " + ret.getClass().getName());
    }

    public void close() {
        Context.exit();
    }

    public String toString() {
        return fun;
    }
}
