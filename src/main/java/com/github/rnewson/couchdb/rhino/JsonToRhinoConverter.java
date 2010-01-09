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

import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.ScriptableObject;

/**
 * Converts JSONObjects to ScriptableObjects, avoid json2.js overhead.
 * 
 * @author rnewson
 * 
 */
public final class JsonToRhinoConverter {

    public static class ScriptableObjectAdapter extends ScriptableObject {

        private static final long serialVersionUID = 1L;

        @Override
        public String getClassName() {
            return "ScriptableObjectAdapter";
        }

    }

    public static Object convert(final Object obj) {
        if (obj instanceof JSONArray) {
            return convertArray((JSONArray) obj);
        } else if (obj instanceof JSONObject) {
            return convertObject((JSONObject) obj);
        } else {
            return obj;
        }
    }

    public static ScriptableObject convertArray(final JSONArray array) {
        final NativeArray result = new NativeArray(array.size());
        for (int i = 0, max = array.size(); i < max; i++) {
            ScriptableObject.putProperty(result, i, convert(array.get(i)));
        }
        return result;
    }

    public static ScriptableObject convertObject(final JSONObject obj) {
        final ScriptableObject result = new ScriptableObjectAdapter();
        for (final Object key : obj.keySet()) {
            final Object value = obj.get(key);
            ScriptableObject.putProperty(result, (String) key, convert(value));
        }
        return result;
    }

}
