package org.apache.couchdb.lucene;

public final class Log {

	public static void outlog(final String fmt, final Object... args) {
		System.out.print("{\"log\":\"");
		System.out.printf(fmt, args);
		System.out.println("\"}");
	}

	public static void errlog(final String fmt, final Object... args) {
		System.err.printf(fmt, args);
		System.err.println();
	}

	public static void outlog(final Exception e) {
		outlog("%s", e.getMessage());
		e.printStackTrace(System.out);
	}

	public static void errlog(final Exception e) {
		errlog("%s", e.getMessage());
		e.printStackTrace();
	}

}
