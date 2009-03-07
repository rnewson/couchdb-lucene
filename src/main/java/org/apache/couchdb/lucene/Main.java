package org.apache.couchdb.lucene;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * Entry point for indexing and searching.
 * 
 * @author rnewson
 * 
 */
public final class Main {

	private static final Logger log = LogManager.getLogger(Main.class);

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
