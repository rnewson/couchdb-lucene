package com.github.rnewson.couchdb.lucene;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.http.client.HttpClient;

import com.github.rnewson.couchdb.lucene.DatabaseIndexer.IndexState;
import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.Database;

public class Lucene2 {

	private final HttpClient client;

	private final File root;

	private final HierarchicalINIConfiguration ini;

	private final Map<Database, Thread> threads = new HashMap<Database, Thread>();

	private final Map<Database, DatabaseIndexer> indexers = new HashMap<Database, DatabaseIndexer>();

	public Lucene2(final HttpClient client, final File root,
			final HierarchicalINIConfiguration ini) {
		this.client = client;
		this.root = root;
		this.ini = ini;
	}

	public IndexState getState(final HttpServletRequest req)
			throws IOException {
		final String[] parts = req.getRequestURI().replaceFirst("/", "").split(
				"/");
		if (parts.length != 4) {
			return null;
		}

		final Configuration section = ini.getSection(parts[0]);
		final String url = section.containsKey("url") ? section
				.getString("url") : null;

		final Couch couch = Couch.getInstance(client, url);
		final Database database = couch.getDatabase(parts[1]);
		final DatabaseIndexer indexer = getIndexer(database);
		ensureRunning(database, indexer);
		return indexer.getState(parts[2], parts[3]);
	}

	private synchronized DatabaseIndexer getIndexer(final Database database)
			throws IOException {
		DatabaseIndexer result = indexers.get(database);
		if (result == null) {
			result = new DatabaseIndexer(client, root, database);
			result.init();
		}
		indexers.put(database, result);
		return result;
	}

	private synchronized void ensureRunning(final Database database,
			final DatabaseIndexer indexer) {
		Thread thread = threads.get(database);
		if (thread == null || !thread.isAlive()) {
			thread = new Thread(indexer);
			threads.put(database, thread);
			thread.start();
		}
	}

}
