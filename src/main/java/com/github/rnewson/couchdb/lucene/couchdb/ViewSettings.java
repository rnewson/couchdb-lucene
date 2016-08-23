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

package com.github.rnewson.couchdb.lucene.couchdb;

import com.github.rnewson.couchdb.lucene.util.Constants;
import org.apache.lucene.document.Field.Store;
import org.json.JSONObject;
import org.mozilla.javascript.NativeObject;

public final class ViewSettings {

    public static ViewSettings getDefaultSettings() {
        return new ViewSettings(Constants.DEFAULT_FIELD, "no", "text", "1.0", null);
    }

    private final Store store;
    private final String field;
    private final FieldType type;
    private final float boost;

    public ViewSettings(final JSONObject json) {
        this(json, getDefaultSettings());
    }

    public ViewSettings(final JSONObject json, final ViewSettings defaults) {
        this(json.optString("field", null), json.optString("store", null), json.optString("type", null), json.optString("boost", null), defaults);
    }

    public ViewSettings(final NativeObject obj) {
        this(obj, getDefaultSettings());
    }

    public ViewSettings(final NativeObject obj, final ViewSettings defaults) {
        this(get(obj, "field"), get(obj, "store"), get(obj, "type"), get(obj, "boost"), defaults);
    }

    private ViewSettings(final String field, final String store, final String type, final String boost, final ViewSettings defaults) {
        this.field = field != null ? field : defaults.getField();
        this.store = store != null ? Store.valueOf(store.toUpperCase()) : defaults.getStore();
        this.type = type != null ? FieldType.valueOf(type.toUpperCase()) : defaults.getFieldType();
        this.boost = boost != null ? Float.valueOf(boost) : defaults.getBoost();
    }

    public float getBoost() {
        return boost;
    }

    public Store getStore() {
        return store;
    }

    public String getField() {
        return field;
    }

    public FieldType getFieldType() {
        return type;
    }

    private static String get(final NativeObject obj, final String key) {
        return obj == null ? null : obj.has(key, null) ? obj.get(key, null).toString() : null;
    }

}
