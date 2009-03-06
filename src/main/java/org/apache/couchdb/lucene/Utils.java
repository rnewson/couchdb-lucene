package org.apache.couchdb.lucene;

import net.sf.json.JSONObject;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;

class Utils {

	public static String throwableToJSON(final Throwable t) {
		return error(t.getMessage() == null ? "Unknown error" : String.format("%s: %s", t.getClass(), t.getMessage()));
	}

	public static String error(final String txt) {
		return new JSONObject().element("code", 500).element("body", txt).toString();
	}

	public static Field text(final String name, final String value, final boolean store) {
		return new Field(name, value, store ? Store.YES : Store.NO, Field.Index.ANALYZED);
	}

	public static Field token(final String name, final String value, final boolean store) {
		return new Field(name, value, store ? Store.YES : Store.NO, Field.Index.NOT_ANALYZED_NO_NORMS);
	}

}
