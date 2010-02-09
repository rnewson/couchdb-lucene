package com.github.rnewson.couchdb.lucene.couchdb;

import net.sf.json.JSONObject;

import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.mozilla.javascript.NativeObject;

import com.github.rnewson.couchdb.lucene.util.Constants;

/**
 * Copyright 2010 Robert Newson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

public final class ViewSettings {

    public static ViewSettings getDefaultSettings() {
        return new ViewSettings(Constants.DEFAULT_FIELD, "analyzed", "no", "string", null);
    }

    private final Index index;
    private final Store store;
    private final String field;
    private final FieldType type;

    public ViewSettings(final JSONObject json) {
        this(json, getDefaultSettings());
    }

    public ViewSettings(final JSONObject json, final ViewSettings defaults) {
        this(json.optString("field", null), json.optString("index", null), json.optString("store", null), json.optString("type", null), defaults);
    }

    public ViewSettings(final NativeObject obj) {
        this(obj, getDefaultSettings());
    }

    public ViewSettings(final NativeObject obj, final ViewSettings defaults) {
        this(get(obj, "field"), get(obj, "index"), get(obj, "store"), get(obj, "type"), defaults);
    }

    private ViewSettings(final String field, final String index, final String store, final String type, final ViewSettings defaults) {
        this.field = field != null ? field : defaults.getField();
        this.index = index != null ? Index.valueOf(index.toUpperCase()) : defaults.getIndex();
        this.store = store != null ? Store.valueOf(store.toUpperCase()) : defaults.getStore();
        this.type = type != null ? FieldType.valueOf(type.toUpperCase()) : defaults.getFieldType();
    }

    public Index getIndex() {
        return index;
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
        return obj == null ? null : obj.has(key, null) ? (String) obj.get(key, null) : null;
    }

}
