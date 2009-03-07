package org.apache.couchdb.lucene;

/**
 * Entry point for indexing and searching.
 * 
 * @author rnewson
 * 
 */
public final class Main {

	public static void main(final String[] args) throws Exception {
		if (args.length >= 1 && args[0].equals("-index")) {
			Index.main(args);
			return;
		}

		if (args.length >= 1 && args[0].equals("-search")) {
			Search.main(args);
			return;
		}

		System.out.println(Utils.error("Invoke with -index or -search only."));
		return;
	}

}
