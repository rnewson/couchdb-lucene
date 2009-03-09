package org.apache.couchdb.lucene;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;

class Utils {

	public static void log(final String fmt, final Object... args) {
		final String msg = String.format(fmt, args);
		System.out.printf("{\"log\":\"%s\"}\n", msg);
	}

	public static String throwableToJSON(final Throwable t) {
		return error(t.getMessage() == null ? "Unknown error" : String.format("%s: %s", t.getClass(), t.getMessage()));
	}

	public static String error(final String txt) {
		return error(500, txt);
	}

	public static String error(final int code, final String txt) {
		return new JSONObject().element("code", code).element("body", StringEscapeUtils.escapeHtml(txt)).toString();
	}

	public static Field text(final String name, final String value, final boolean store) {
		return new Field(name, value, store ? Store.YES : Store.NO, Field.Index.ANALYZED);
	}

	public static Field token(final String name, final String value, final boolean store) {
		return new Field(name, value, store ? Store.YES : Store.NO, Field.Index.NOT_ANALYZED_NO_NORMS);
	}

}
