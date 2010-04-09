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
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.http.client.HttpClient;

import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.util.ServletUtils;

public final class LuceneServlet extends HttpServlet {

	private static final JSONObject JSON_SUCCESS = JSONObject
			.fromObject("{\"ok\":true}");

	private static final long serialVersionUID = 1L;

	private final HttpClient client;

	private final File root;

	private final HierarchicalINIConfiguration ini;

	private final Map<Database, Thread> threads = new HashMap<Database, Thread>();

	private final Map<Database, DatabaseIndexer> indexers = new HashMap<Database, DatabaseIndexer>();

	public LuceneServlet(final HttpClient client, final File root,
			final HierarchicalINIConfiguration ini) {
		this.client = client;
		this.root = root;
		this.ini = ini;
	}

	@Override
	protected void doGet(final HttpServletRequest req,
			final HttpServletResponse resp) throws ServletException,
			IOException {
		final DatabaseIndexer indexer = getIndexer(req);

		switch (pathParts(req).length) {
		case 0:
			handleWelcomeReq(req, resp);
			return;
		case 4:
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
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		final String[] pathParts = pathParts(req);
		switch (pathParts.length) {
		case 3:
			if ("_cleanup".equals(pathParts[2])) {
				// handleCleanupReq(pathParts[0], req, resp);
				return;
			}
			break;
		case 5:
			// handleAdminReq(pathParts[4], path, req, resp);
			return;
		}
		ServletUtils.sendJSONError(req, resp, 400, "bad_request");
	}

	private void handleWelcomeReq(final HttpServletRequest req,
			final HttpServletResponse resp) throws ServletException,
			IOException {
		final JSONObject welcome = new JSONObject();
		welcome.put("couchdb-lucene", "Welcome");
		welcome.put("version", "0.5.0");
		ServletUtils.writeJSON(resp, welcome);
	}

	/*
	 * private void handleCleanupReq(final String key, final HttpServletRequest
	 * req, final HttpServletResponse resp) throws ServletException, IOException
	 * { final HttpClient client = HttpClientFactory.getInstance(); final Couch
	 * couch = Couch.getInstance(client, IndexPath.url(ini, key));
	 * 
	 * final Set<String> dbKeep = new HashSet<String>();
	 * 
	 * for (final String dbname : couch.getAllDatabases()) { final Database db =
	 * couch.getDatabase(dbname); final UUID uuid = db.getUuid(); if (uuid ==
	 * null) { continue; }
	 * 
	 * dbKeep.add(uuid.toString());
	 * 
	 * final Set<String> viewKeep = new HashSet<String>(); for (final
	 * DesignDocument ddoc : db.getAllDesignDocuments()) { for (final View view
	 * : ddoc.getAllViews()) { viewKeep.add(view.getDigest()); } } // Delete all
	 * indexes except the keepers. for (final File dir :
	 * lucene.getUuidDir(db.getUuid()).listFiles()) { if
	 * (!viewKeep.contains(dir.getName())) { LOG.info("Cleaning old index at " +
	 * dir); FileUtils.deleteDirectory(dir); } } }
	 * 
	 * // Delete all directories except the keepers. for (final File dir :
	 * lucene.getRootDir().listFiles()) { if (!dbKeep.contains(dir.getName())) {
	 * LOG.info("Cleaning old index at " + dir); FileUtils.deleteDirectory(dir);
	 * } }
	 * 
	 * resp.setStatus(202); ServletUtils.writeJSON(resp, JSON_SUCCESS); }
	 */

	/*
	 * private void handleAdminReq(final String command, final IndexPath path,
	 * final HttpServletRequest req, final HttpServletResponse resp) throws
	 * ServletException, IOException { if ("_expunge".equals(command)) {
	 * LOG.info("Expunging deletes from " + path); lucene.withWriter(path, new
	 * WriterCallback() { public boolean callback(final IndexWriter writer)
	 * throws IOException { writer.expungeDeletes(false); return false; }
	 * 
	 * public void onMissing() throws IOException { resp.sendError(404); } });
	 * ServletUtils.setResponseContentTypeAndEncoding(req, resp);
	 * resp.setStatus(202); ServletUtils.writeJSON(resp, JSON_SUCCESS); return;
	 * }
	 * 
	 * if ("_optimize".equals(command)) { LOG.info("Optimizing " + path);
	 * lucene.withWriter(path, new WriterCallback() { public boolean
	 * callback(final IndexWriter writer) throws IOException {
	 * writer.optimize(false); return false; }
	 * 
	 * public void onMissing() throws IOException { resp.sendError(404); } });
	 * ServletUtils.setResponseContentTypeAndEncoding(req, resp);
	 * resp.setStatus(202); ServletUtils.writeJSON(resp, JSON_SUCCESS); return;
	 * } }
	 */

	private DatabaseIndexer getIndexer(final HttpServletRequest req)
			throws IOException {
		final Configuration section = ini.getSection(pathParts(req)[0]);
		final String url = section.containsKey("url") ? section
				.getString("url") : null;

		final Couch couch = new Couch(client, url);
		final Database database = couch.getDatabase(pathParts(req)[1]);
		return getIndexer(database);
	}

	private synchronized DatabaseIndexer getIndexer(final Database database)
			throws IOException {
		DatabaseIndexer result = indexers.get(database);
		Thread thread = threads.get(database);
		if (result == null || thread == null || !thread.isAlive()) {
			result = new DatabaseIndexer(client, root, database);
			indexers.put(database, result);
			thread = new Thread(result);
			threads.put(database, thread);
			thread.start();
		} 
		return result;
	}

	private String[] pathParts(final HttpServletRequest req) {
		return req.getRequestURI().replaceFirst("/", "").split("/");
	}

	private boolean isStaleOk(final HttpServletRequest req) {
		return "ok".equals(req.getParameter("stale"));
	}

}
