package com.github.rnewson.couchdb.rhino;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class JSONArrayAdapter extends ScriptableObject {

	private static final long serialVersionUID = 3L;
	
	final JSONArray array;
	final int size;
	
	public JSONArrayAdapter(JSONArray _array) {
		this.array = _array;
		this.size = _array.size();
	}

	@Override
	public String getClassName() {
		return "JSONArrayAdapter";
	}
	
	@Override
	public Object get(String name, Scriptable start) {
		if (name.equals("length"))
			return size;
		return super.get(name, start);
	}
	
	@Override
	public Object get(int index, Scriptable start) {
		if ((index >= 0) && (index < size)) {
			Object value = array.get(index);
			
			if (value instanceof JSONObject)
				return new JSONDocumentAdapter((JSONObject) value);
			if (value instanceof JSONArray)
				return new JSONArrayAdapter((JSONArray) value);
			return value;
		}
		
		return super.get(index, start);
	}
}
