package com.github.rnewson.couchdb.rhino;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class JSONDocumentAdapter extends ScriptableObject {

	private static final long serialVersionUID = 2L;
	
	final JSONObject doc;
	
	public JSONDocumentAdapter(JSONObject _doc) {
		this.doc = _doc;
	}

	@Override
	public String getClassName() {
		return "JSONDocumentAdapter";
	}
	
	@Override
	public Object get(String name, Scriptable start) {
		if (doc.has(name)) {
			Object value = doc.get(name);
			if (value instanceof JSONObject)
				return new JSONDocumentAdapter((JSONObject) value);
			if (value instanceof JSONArray)
				return new JSONArrayAdapter((JSONArray) value);
			return value;
		}
		
		return super.get(name, start);
	}
}
