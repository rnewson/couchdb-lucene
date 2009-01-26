package org.apache.couchdb.lucene;

import net.sf.json.JSONObject;

class Utils {

	public static JSONObject throwableToJSON(final Throwable t) {
		return new JSONObject().element("code", "500").element("body",
				t.getMessage() == null ? "Unknown error" : t.getMessage());
	}

}
