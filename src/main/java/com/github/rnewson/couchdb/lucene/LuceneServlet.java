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

import static com.github.rnewson.couchdb.lucene.util.ServletUtils.getBooleanParameter;
import static com.github.rnewson.couchdb.lucene.util.ServletUtils.getIntParameter;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.HttpClient;
import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;

import com.github.rnewson.couchdb.lucene.Lucene.ReaderCallback;
import com.github.rnewson.couchdb.lucene.Lucene.SearcherCallback;
import com.github.rnewson.couchdb.lucene.Lucene.WriterCallback;
import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.CouchDocument;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.couchdb.DesignDocument;
import com.github.rnewson.couchdb.lucene.couchdb.View;
import com.github.rnewson.couchdb.lucene.util.IndexPath;
import com.github.rnewson.couchdb.lucene.util.ServletUtils;
import com.github.rnewson.couchdb.lucene.util.StopWatch;
import com.github.rnewson.couchdb.lucene.util.Utils;

public final class LuceneServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final JSONObject JSON_SUCCESS = JSONObject
			.fromObject("{\"ok\":true}");

	private static final Logger LOG = Logger.getLogger(LuceneServlet.class);

	private Lucene lucene;

	private HierarchicalINIConfiguration ini;

	public void setLucene(final Lucene lucene) {
		this.lucene = lucene;
	}

	public void setConfiguration(final HierarchicalINIConfiguration ini) {
		this.ini = ini;
	}

	@Override
	protected void doGet(final HttpServletRequest req,
			final HttpServletResponse resp) throws ServletException,
			IOException {
		final String[] pathParts = IndexPath.parts(req);
		final IndexPath path = IndexPath.parse(ini, req);
		if (path != null) {
			lucene.startIndexing(path, true);
		}
		ServletUtils.setResponseContentTypeAndEncoding(req, resp);

		switch (pathParts.length) {
		case 0:
			handleWelcomeReq(req, resp);
			return;
		case 4:
			if (req.getParameter("q") == null) {
				handleInfoReq(req, resp);
			} else {
				handleSearchReq(req, resp);
			}
			return;
		}

		ServletUtils.sendJSONError(req, resp, 400, "bad_request");
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		final String[] pathParts = IndexPath.parts(req);
		final IndexPath path = IndexPath.parse(ini, req);
		if (path != null) {
			lucene.startIndexing(path, true);
		}
		ServletUtils.setResponseContentTypeAndEncoding(req, resp);

		switch (pathParts.length) {
		case 3:
			if ("_cleanup".equals(pathParts[2])) {
				handleCleanupReq(pathParts[0], req, resp);
				return;
			}
			break;
		case 5:
			handleAdminReq(pathParts[4], path, req, resp);
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

	private void handleSearchReq(final HttpServletRequest req,
			final HttpServletResponse resp) throws ServletException,
			IOException {
		final boolean debug = getBooleanParameter(req, "debug");
		final boolean rewrite_query = getBooleanParameter(req, "rewrite");
		final boolean staleOk = Utils.getStaleOk(req);

		final IndexPath path = IndexPath.parse(ini, req);

		final ViewIndexer indexer = lucene.startIndexing(path, staleOk);

		final SearcherCallback callback = new SearcherCallback() {

			public void callback(final IndexSearcher searcher,
					final QueryParser parser,
					final String version) throws IOException {
				// Check for 304 - Not Modified.
				if (!debug && version.equals(req.getHeader("If-None-Match"))) {
					resp.setStatus(304);
					return;
				}

				// Parse query.
				final String[] queries = req.getParameterValues("q");
				final JSONArray arr = new JSONArray();

				for (final String query : queries) {
					try {
						arr.add(performQuery(debug, rewrite_query, searcher,
								version, parser.parse(query)));
					} catch (final ParseException e) {
						ServletUtils.sendJSONError(req, resp, 400,
								"Bad query syntax: " + e.getMessage());
						return;
					}
				}

				ServletUtils.setResponseContentTypeAndEncoding(req, resp);

				// Cache-related headers.
				resp.setHeader("ETag", version);
				resp.setHeader("Cache-Control", "must-revalidate");

				final JSON json = arr.size() > 1 ? arr : arr.getJSONObject(0);

				// Format response body.
				final String callback = req.getParameter("callback");
				final String body;
				if (callback != null) {
					body = String.format("%s(%s)", callback, json);
				} else {
					body = json.toString(debug ? 2 : 0);
				}

				final Writer writer = resp.getWriter();
				try {
					writer.write(body);
				} finally {
					writer.close();
				}
			}

			private JSONObject performQuery(final boolean debug,
					final boolean rewrite_query, final IndexSearcher searcher,
					final String version, final Query q) throws IOException,
					ParseException {
				final JSONObject result = new JSONObject();
				result.put("q", q.toString());
				if (debug) {
					result.put("plan", QueryPlan.toPlan(q));
				}
				result.put("etag", version);

				if (rewrite_query) {
					final Query rewritten_q = q.rewrite(searcher
							.getIndexReader());
					result.put("rewritten_q", rewritten_q.toString());

					final JSONObject freqs = new JSONObject();

					final Set<Term> terms = new HashSet<Term>();
					rewritten_q.extractTerms(terms);
					for (final Object term : terms) {
						final int freq = searcher.docFreq((Term) term);
						freqs.put(term, freq);
					}
					result.put("freqs", freqs);
				} else {
					// Perform the search.
					final TopDocs td;
					final StopWatch stopWatch = new StopWatch();

					final boolean include_docs = getBooleanParameter(req,
							"include_docs");
					final int limit = getIntParameter(req, "limit", 25);
					final Sort sort = CustomQueryParser.toSort(req
							.getParameter("sort"));
					final int skip = getIntParameter(req, "skip", 0);

					if (sort == null) {
						td = searcher.search(q, null, skip + limit);
					} else {
						td = searcher.search(q, null, skip + limit, sort);
					}
					stopWatch.lap("search");

					// Fetch matches (if any).
					final int max = max(0, min(td.totalHits - skip, limit));
					final JSONArray rows = new JSONArray();
					final String[] fetch_ids = new String[max];
					for (int i = skip; i < skip + max; i++) {
						final Document doc = searcher.doc(td.scoreDocs[i].doc);
						final JSONObject row = new JSONObject();
						final JSONObject fields = new JSONObject();

						// Include stored fields.
						for (final Object f : doc.getFields()) {
							final Field fld = (Field) f;

							if (!fld.isStored()) {
								continue;
							}
							final String name = fld.name();
							final String value = fld.stringValue();
							if (value != null) {
								if ("_id".equals(name)) {
									row.put("id", value);
								} else {
									if (!fields.has(name)) {
										fields.put(name, value);
									} else {
										final Object obj = fields.get(name);
										if (obj instanceof String) {
											final JSONArray arr = new JSONArray();
											arr.add(obj);
											arr.add(value);
											fields.put(name, arr);
										} else {
											assert obj instanceof JSONArray;
											((JSONArray) obj).add(value);
										}
									}
								}
							}
						}

						if (!Float.isNaN(td.scoreDocs[i].score)) {
							row.put("score", td.scoreDocs[i].score);
						}
						// Include sort order (if any).
						if (td instanceof TopFieldDocs) {
							final FieldDoc fd = (FieldDoc) ((TopFieldDocs) td).scoreDocs[i];
							row.put("sort_order", fd.fields);
						}
						// Fetch document (if requested).
						if (include_docs) {
							fetch_ids[i - skip] = doc.get("_id");
						}
						if (fields.size() > 0) {
							row.put("fields", fields);
						}
						rows.add(row);
					}
					// Fetch documents (if requested).
					if (include_docs && fetch_ids.length > 0) {
						final HttpClient httpClient = HttpClientFactory
								.getInstance();
						try {
							final Couch couch = Couch.getInstance(httpClient,
									path.getUrl());
							final Database database = couch.getDatabase(path
									.getDatabase());
							final List<CouchDocument> fetched_docs = database
									.getDocuments(fetch_ids);
							for (int i = 0; i < max; i++) {
								rows.getJSONObject(i).put("doc",
										fetched_docs.get(i).asJson());
							}
						} finally {
							httpClient.getConnectionManager().shutdown();
						}
					}
					stopWatch.lap("fetch");

					result.put("skip", skip);
					result.put("limit", limit);
					result.put("total_rows", td.totalHits);
					result.put("search_duration", stopWatch
							.getElapsed("search"));
					result.put("fetch_duration", stopWatch.getElapsed("fetch"));
					// Include sort info (if requested).
					if (td instanceof TopFieldDocs) {
						result.put("sort_order", CustomQueryParser
								.toString(((TopFieldDocs) td).fields));
					}
					result.put("rows", rows);
				}
				return result;
			}

			public void onMissing() throws IOException {
				ServletUtils.sendJSONError(req, resp, 404, "Index for " + path
						+ " is missing.");
			}
		};

		lucene.withSearcher(path, staleOk, callback);
	}

	private void handleInfoReq(final HttpServletRequest req,
			final HttpServletResponse resp) throws ServletException,
			IOException {
		final IndexPath path = IndexPath.parse(ini, req);
		lucene.withReader(path, Utils.getStaleOk(req), new ReaderCallback() {
			public void callback(final IndexReader reader) throws IOException {
				final JSONObject result = new JSONObject();
				result.put("current", reader.isCurrent());
				result
						.put("disk_size", Utils.directorySize(reader
								.directory()));
				result.put("doc_count", reader.numDocs());
				result.put("doc_del_count", reader.numDeletedDocs());
				final JSONArray fields = new JSONArray();
				for (final Object field : reader
						.getFieldNames(FieldOption.INDEXED)) {
					if (((String) field).startsWith("_")) {
						continue;
					}
					fields.add(field);
				}
				result.put("fields", fields);
				result.put("last_modified", Long.toString(IndexReader
						.lastModified(reader.directory())));
				result.put("optimized", reader.isOptimized());
				result.put("ref_count", reader.getRefCount());

				final JSONObject info = new JSONObject();
				info.put("code", 200);
				info.put("json", result);

				ServletUtils.setResponseContentTypeAndEncoding(req, resp);
				final Writer writer = resp.getWriter();
				try {
					writer.write(result.toString());
				} finally {
					writer.close();
				}
			}

			public void onMissing() throws IOException {
				resp.sendError(404);
			}
		});
	}

	private void handleCleanupReq(final String key,
			final HttpServletRequest req, final HttpServletResponse resp)
			throws ServletException, IOException {
		final HttpClient client = HttpClientFactory.getInstance();
		final Couch couch = Couch.getInstance(client, IndexPath.url(ini, key));

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
				for (final View view : ddoc.getAllViews()) {
					viewKeep.add(view.getDigest());
				}
			}
			// Delete all indexes except the keepers.
			for (final File dir : lucene.getUuidDir(db.getUuid()).listFiles()) {
				if (!viewKeep.contains(dir.getName())) {
					LOG.info("Cleaning old index at " + dir);
					FileUtils.deleteDirectory(dir);
				}
			}
		}

		// Delete all directories except the keepers.
		for (final File dir : lucene.getRootDir().listFiles()) {
			if (!dbKeep.contains(dir.getName())) {
				LOG.info("Cleaning old index at " + dir);
				FileUtils.deleteDirectory(dir);
			}
		}

		resp.setStatus(202);
		ServletUtils.writeJSON(resp, JSON_SUCCESS);
	}

	private void handleAdminReq(final String command, final IndexPath path,
			final HttpServletRequest req, final HttpServletResponse resp)
			throws ServletException, IOException {
		if ("_expunge".equals(command)) {
			LOG.info("Expunging deletes from " + path);
			lucene.withWriter(path, new WriterCallback() {
				public boolean callback(final IndexWriter writer)
						throws IOException {
					writer.expungeDeletes(false);
					return false;
				}

				public void onMissing() throws IOException {
					resp.sendError(404);
				}
			});
			ServletUtils.setResponseContentTypeAndEncoding(req, resp);
			resp.setStatus(202);
			ServletUtils.writeJSON(resp, JSON_SUCCESS);
			return;
		}

		if ("_optimize".equals(command)) {
			LOG.info("Optimizing " + path);
			lucene.withWriter(path, new WriterCallback() {
				public boolean callback(final IndexWriter writer)
						throws IOException {
					writer.optimize(false);
					return false;
				}

				public void onMissing() throws IOException {
					resp.sendError(404);
				}
			});
			ServletUtils.setResponseContentTypeAndEncoding(req, resp);
			resp.setStatus(202);
			ServletUtils.writeJSON(resp, JSON_SUCCESS);
			return;
		}
	}

}
