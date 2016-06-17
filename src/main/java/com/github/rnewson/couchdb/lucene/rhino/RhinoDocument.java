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

package com.github.rnewson.couchdb.lucene.rhino;

import com.github.rnewson.couchdb.lucene.Tika;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.couchdb.FieldType;
import com.github.rnewson.couchdb.lucene.couchdb.ViewSettings;
import com.github.rnewson.couchdb.lucene.util.Utils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.mozilla.javascript.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Collect data from the user.
 *
 * @author rnewson
 */
public final class RhinoDocument extends ScriptableObject {

    private static class RhinoAttachment {
        private String attachmentName;
        private String fieldName;
    }

    private static class RhinoField {
        private NativeObject settings;
        private Object value;
    }

    private static final long serialVersionUID = 1L;

    public static Scriptable jsConstructor(final Context cx, final Object[] args, final Function ctorObj, final boolean inNewExpr) {
        final RhinoDocument doc = new RhinoDocument();
        if (args.length >= 2) {
            jsFunction_add(cx, doc, args, ctorObj);
        }
        return doc;
    }

    public static void jsFunction_add(final Context cx, final Scriptable thisObj, final Object[] args, final Function funObj) {
        final RhinoDocument doc = checkInstance(thisObj);

        if (args.length < 1 || args.length > 2) {
            throw Context.reportRuntimeError("Invalid number of arguments.");
        }

        if (args[0] == null) {
            // Ignore.
            return;
        }

        if (args[0] instanceof Undefined) {
            // Ignore
            return;
        }

        final String className = args[0].getClass().getName();

        if (className.equals("org.mozilla.javascript.NativeDate")) {
            args[0] = (Date) Context.jsToJava(args[0], Date.class);
        }

        if (!className.startsWith("java.lang.") &&
                !className.equals("org.mozilla.javascript.NativeObject") &&
                !className.equals("org.mozilla.javascript.NativeDate")) {
            throw Context.reportRuntimeError(className + " is not supported.");
        }

        if (args.length == 2 && (args[1] == null || args[1] instanceof NativeObject == false)) {
            throw Context.reportRuntimeError("second argument must be an object.");
        }

        final RhinoField field = new RhinoField();
        field.value = args[0];
        if (args.length == 2) {
            field.settings = (NativeObject) args[1];
        }

        doc.fields.add(field);
    }

    public static void jsFunction_attachment(final Context cx, final Scriptable thisObj, final Object[] args, final Function funObj)
            throws IOException {
        final RhinoDocument doc = checkInstance(thisObj);
        if (args.length < 2) {
            throw Context.reportRuntimeError("Invalid number of arguments.");
        }

        final RhinoAttachment attachment = new RhinoAttachment();
        attachment.fieldName = args[0].toString();
        attachment.attachmentName = args[1].toString();
        doc.attachments.add(attachment);
    }

    private static RhinoDocument checkInstance(final Scriptable obj) {
        if (obj == null || !(obj instanceof RhinoDocument)) {
            throw Context.reportRuntimeError("called on incompatible object.");
        }
        return (RhinoDocument) obj;
    }

    private final List<RhinoAttachment> attachments = new ArrayList<>();

    private final List<RhinoField> fields = new ArrayList<>();

    public RhinoDocument() {
    }

    public Document toDocument(final String id, final ViewSettings defaults, final Database database) throws IOException,
            ParseException {
        final Document result = new Document();

        // Add id.
        result.add(Utils.token("_id", id, true));

        // Add user-supplied fields.
        for (final RhinoField field : fields) {
            addField(field, defaults, result);
        }

        // Parse user-requested attachments.
        for (final RhinoAttachment attachment : attachments) {
            addAttachment(attachment, id, database, result);
        }

        return result;
    }

    @Override
    public String getClassName() {
        return "Document";
    }

    private void addAttachment(final RhinoAttachment attachment, final String id, final Database database, final Document out)
            throws IOException {
        final ResponseHandler<Void> handler = new ResponseHandler<Void>() {

            public Void handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                final HttpEntity entity = response.getEntity();
                try {
                    Tika.INSTANCE.parse(entity.getContent(), entity.getContentType().getValue(), attachment.fieldName, out);
                } finally {
                    entity.consumeContent();
                }
                return null;
            }
        };

        database.handleAttachment(id, attachment.attachmentName, handler);
    }

    private void addField(final RhinoField field, final ViewSettings defaults, final Document out) throws ParseException {
        final ViewSettings settings = new ViewSettings(field.settings, defaults);
        final FieldType type = settings.getFieldType();
        type.addFields(settings.getField(), field.value, settings, out);
    }

}
