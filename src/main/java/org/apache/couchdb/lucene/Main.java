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
		index.start();

		// Main processing loop.
		final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
		String line = null;
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

				// Execute query.
				System.out.println(index.query(db, q, sort, skip, limit));
			} catch (final Exception e) {
				log.warn("Exception in main loop (line=\"" + line + "\")", e);
				System.out.println(Utils.throwableToJSON(e));
			}
		}

		index.stop();
	}

}
