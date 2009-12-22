package com.github.rnewson.couchdb.rhino;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.ScriptableObject;

/**
 * Converts JSONObjects to ScriptableObjects, avoid json2.js overhead.
 * 
 * @author robertnewson
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
