package com.github.rnewson.couchdb.lucene;

/**
 * Copyright 2010 Robert Newson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.log4j.Logger;

import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.couchdb.DesignDocument;
import com.github.rnewson.couchdb.lucene.couchdb.View;
import com.github.rnewson.couchdb.lucene.util.ServletUtils;

public final class LuceneServlet extends HttpServlet {

	private static final JSONObject JSON_SUCCESS = JSONObject
			.fromObject("{\"ok\":true}");

	private static final Logger LOG = Logger.getLogger(LuceneServlet.class);

	private static final long serialVersionUID = 1L;

	private final HttpClient client;

	private final Map<Database, DatabaseIndexer> indexers = new HashMap<Database, DatabaseIndexer>();

	private final HierarchicalINIConfiguration ini;

	private final File root;

	private final Map<Database, Thread> threads = new HashMap<Database, Thread>();

	public LuceneServlet(final HttpClient client, final File root,
			final HierarchicalINIConfiguration ini) {
		this.client = client;
		this.root = root;
		this.ini = ini;
	}

	private void cleanup(final HttpServletRequest req,
			final HttpServletResponse resp) throws IOException {
		final Couch couch = getCouch(req);
		final Set<String> dbKeep = new HashSet<String>();

		for (final String dbname : couch.getAllDatabases()) {
			final Database db = couch.getDatabase(dbname);
			final UUID uuid = db.getUuid();
			if (uuid == null) {
				continue;
			}
			dbKeep.add(uuid.toString());

			final Set<String> viewKeep = new HashSet<String>();

			for (final DesignDocument ddoc : db.getAllDesignDocuments()) {
				for (final View view : ddoc.getAllViews().values()) {
					viewKeep.add(view.getDigest());
				}
			}

			// Delete all indexes except the keepers.
			for (final File dir : DatabaseIndexer.uuidDir(root, db.getUuid())
					.listFiles()) {
				if (!viewKeep.contains(dir.getName())) {
					LOG.info("Cleaning old index at " + dir);
					FileUtils.deleteDirectory(dir);
				}
			}
		}

		// Delete all directories except the keepers.
		for (final File dir : root.listFiles()) {
			if (!dbKeep.contains(dir.getName())) {
				LOG.info("Cleaning old index at " + dir);
				FileUtils.deleteDirectory(dir);
			}
		}

		resp.setStatus(202);
		ServletUtils.writeJSON(req, resp, JSON_SUCCESS);
	}

	private Couch getCouch(final HttpServletRequest req) throws IOException {
		final Configuration section = ini.getSection(new PathParts(req)
				.getKey());
		final String url = section.containsKey("url") ? section
				.getString("url") : null;
		return new Couch(client, url);
	}

	private synchronized DatabaseIndexer getIndexer(final Database database)
			throws IOException {
		DatabaseIndexer result = indexers.get(database);
		Thread thread = threads.get(database);
		if (result == null || thread == null || !thread.isAlive()) {
			result = new DatabaseIndexer(client, root, database, ini);
			thread = new Thread(result);
			thread.start();
			result.awaitInitialization();
			if (result.isClosed()) {
			    return null;
			} else {
	            indexers.put(database, result);
		         threads.put(database, thread);
			}
		}

		return result;
	}

	private DatabaseIndexer getIndexer(final HttpServletRequest req)
			throws IOException {
		final Couch couch = getCouch(req);
		final Database database = couch.getDatabase(new PathParts(req)
				.getDatabaseName());
		return getIndexer(database);
	}

	private void handleWelcomeReq(final HttpServletRequest req,
			final HttpServletResponse resp) throws ServletException,
			IOException {
	    final Package p = this.getClass().getPackage();
		final JSONObject welcome = new JSONObject();
		welcome.put("couchdb-lucene", "Welcome");
		welcome.put("version", p.getImplementationVersion());
		ServletUtils.writeJSON(req, resp, welcome);
	}

	@Override
	protected void doGet(final HttpServletRequest req,
			final HttpServletResponse resp) throws ServletException,
			IOException {
		switch (StringUtils.countMatches(req.getRequestURI(), "/")) {
		case 1:
			handleWelcomeReq(req, resp);
			return;
		case 5:
			final DatabaseIndexer indexer = getIndexer(req);
			if (indexer == null) {
			    ServletUtils.sendJSONError(req, resp, 500, "error_creating_index");
			    return;
			}
			
			if (req.getParameter("q") == null) {
				indexer.info(req, resp);
			} else {
				indexer.search(req, resp);
			}
			return;
		}

		ServletUtils.sendJSONError(req, resp, 400, "bad_request");
	}

	@Override
	protected void doPost(final HttpServletRequest req,
			final HttpServletResponse resp) throws ServletException,
			IOException {
		switch (StringUtils.countMatches(req.getRequestURI(), "/")) {
		case 3:
			if (req.getPathInfo().endsWith("/_cleanup")) {
				cleanup(req, resp);
				return;
			}
			break;
		case 6:
			final DatabaseIndexer indexer = getIndexer(req);
			indexer.admin(req, resp);
			return;
		}
		ServletUtils.sendJSONError(req, resp, 400, "bad_request");
	}

}
