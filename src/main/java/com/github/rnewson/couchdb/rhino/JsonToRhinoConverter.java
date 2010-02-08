package com.github.rnewson.couchdb.rhino;

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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * Converts JSONObjects to Scriptables, avoid json2.js overhead.
 * 
 * @author rnewson
 * 
 */
public final class JsonToRhinoConverter {

    public static Object convert(
            final Context context,
            final ScriptableObject scope,
            final Object obj) {
        if (obj instanceof JSONArray) {
            return convertArray(context, scope, (JSONArray) obj);
        } else if (obj instanceof JSONObject) {
            return convertObject(context, scope, (JSONObject) obj);
        } else {
            return obj;
        }
    }

    public static Scriptable convertArray(
            final Context context,
            final ScriptableObject scope,
            final JSONArray array) {
        final Scriptable result = context.newArray(scope, array.size());
        for (int i = 0, max = array.size(); i < max; i++) {
            ScriptableObject.putProperty(result, i, convert(context, scope, array.get(i)));
        }
        return result;
    }

    public static Scriptable convertObject(
            final Context context,
            final ScriptableObject scope,
            final JSONObject obj) {
        final Scriptable result = context.newObject(scope);
        for (final Object key : obj.keySet()) {
            final Object value = obj.get(key);
            ScriptableObject.putProperty(result, (String) key, convert(context, scope, value));
        }
        return result;
    }

}
