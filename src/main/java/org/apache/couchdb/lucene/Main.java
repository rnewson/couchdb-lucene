package org.apache.couchdb.lucene;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import net.sf.json.JSONObject;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * Entry point. This class is invoked by couchdb directly using the following
 * configuration;
 * 
 * <pre>
 * [external]
 * fti = /usr/bin/java -cp /path/to/couchdb-lucene.jar
 * 
 * [httpd_db_handlers]
 * _fti = {couch_httpd_external, handle_external_req, &lt;&lt;&quot;fti&quot;&gt;&gt;}
 * </pre>
 * 
 * @author rnewson
 * 
 */
public final class Main {

	private static final Logger log = LogManager.getLogger(Main.class);

	public static void main(final String[] args) throws IOException {
		final Index index = new Index();
		final Thread startupThread = new Thread(new Runnable() {

			public void run() {
				try {
					index.start();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		startupThread.start();

		final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
		String line = null;

		// Promptly returns errors until started.
		while (startupThread.isAlive() && (line=reader.readLine()) != null) {
			System.out.println(Utils.error("couchdb-lucene is unavailable."));
		}

		// Main processing loop.
		while ((line = reader.readLine()) != null) {
			try {
				// Parse query.
				final JSONObject obj = JSONObject.fromObject(line);

				final String db = obj.getJSONObject("info").getString("db_name");
				final JSONObject query = obj.getJSONObject("query");
				final String q = query.getString("q");
				final String sort = query.optString("sort", null);
				final int skip = query.optInt("skip", 0);
				final int limit = query.optInt("limit", 25);
				final boolean debug = query.optBoolean("debug", false);
				final boolean asc = query.optBoolean("asc", true);
				final boolean include_docs = query.optBoolean("include_docs", false);

				// Execute query.
				System.out.println(index.query(db, q, sort, asc, skip, limit, include_docs, debug));
			} catch (final Exception e) {
				log.warn("Exception in main loop (line=\"" + line + "\")", e);
				System.out.println(Utils.throwableToJSON(e));
			}
		}

		index.stop();
	}

}
