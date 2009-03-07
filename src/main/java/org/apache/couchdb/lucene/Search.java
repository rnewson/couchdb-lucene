package org.apache.couchdb.lucene;

import java.util.Scanner;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.NIOFSDirectory;

/**
 * Search entry point.
 */
public final class Search {

	public static void main(final String[] args) throws Exception {
		IndexReader reader = null;
		IndexSearcher searcher = null;

		final Scanner scanner = new Scanner(System.in);
		while (scanner.hasNextLine()) {
			if (reader == null) {
				// Open a reader and searcher if index exists.
				if (IndexReader.indexExists(Config.INDEX_DIR)) {
					reader = IndexReader.open(NIOFSDirectory.getDirectory(Config.INDEX_DIR), true);
					searcher = new IndexSearcher(reader);
				}
			} else {
				// Refresh reader and searcher if necessary.
				final IndexReader newReader = reader.reopen();
				if (reader != newReader) {
					final IndexReader oldReader = reader;
					reader = newReader;
					searcher = new IndexSearcher(reader);
					oldReader.close();
				}
			}

			final String line = scanner.nextLine();

			// Process search request if index exists.
			if (searcher == null) {
				System.out.println("{\"code\":503,\"body\":\"couchdb-lucene not available.\"}");
			} else {
				final SearchRequest request = new SearchRequest(line);
				try {
					final String result = request.execute(searcher);
					System.out.println(result);
				} catch (final Exception e) {
					System.out.printf("{\"code\":400,\"body\":\"%s\"}\n", StringEscapeUtils.escapeHtml(e.getMessage()));
				}
			}
		}
		if (reader != null) {
			reader.close();
		}
	}

}
