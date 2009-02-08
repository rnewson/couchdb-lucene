package org.apache.couchdb.lucene;

import net.sf.json.JSONObject;

class Utils {

	public static String throwableToJSON(final Throwable t) {
		return error(t.getMessage() == null ? "Unknown error" : String.format("%s: %s", t.getClass(), t.getMessage()));
	}

	public static String error(final String txt) {
		return new JSONObject().element("code", "500").element("body", txt).toString();
	}

}
