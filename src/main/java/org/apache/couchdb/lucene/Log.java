package org.apache.couchdb.lucene;

public final class Log {

	public static void log(final String fmt, final Object... args) {
		System.out.print("log,");
		System.out.printf(fmt, args);
		System.out.println();
	}

	public static void log(final Exception e) {
		log("%s", e.getMessage());
	}

}
