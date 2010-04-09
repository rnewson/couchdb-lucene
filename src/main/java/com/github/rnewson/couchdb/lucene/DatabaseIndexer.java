package com.github.rnewson.couchdb.lucene;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;

import com.github.rnewson.couchdb.lucene.couchdb.CouchDocument;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.couchdb.DesignDocument;
import com.github.rnewson.couchdb.lucene.couchdb.View;
import com.github.rnewson.couchdb.lucene.util.Constants;
import com.github.rnewson.couchdb.lucene.util.ServletUtils;
import com.github.rnewson.couchdb.lucene.util.StopWatch;
import com.github.rnewson.couchdb.lucene.util.Utils;

public final class DatabaseIndexer implements Runnable, ResponseHandler<Void> {

	private final class RestrictiveClassShutter implements ClassShutter {
		public boolean visibleToScripts(final String fullClassName) {
			return false;
		}
	}

	private static class IndexState {

		private final DocumentConverter converter;
		private final IndexWriter writer;
		private final QueryParser parser;
		private final CountDownLatch latch = new CountDownLatch(1);

		private long pending_seq;
		private boolean dirty;
		private String etag;
		private IndexReader reader;

		public IndexState(final DocumentConverter converter,
				final IndexWriter writer, final QueryParser parser, final long pending_seq) {
			this.converter = converter;
			this.writer = writer;
			this.parser = parser;
			this.pending_seq = pending_seq;
		}

		private synchronized String getEtag() {
			return etag;
		}

		private synchronized boolean notModified(final HttpServletRequest req) {
			return etag != null && etag.equals(req.getHeader("If-None-Match"));
		}

		private synchronized void close() throws IOException {
			if (reader != null)
				reader.close();
			if (writer != null)
				writer.rollback();
		}

		public IndexSearcher borrowSearcher(final boolean staleOk)
				throws IOException {
			return new IndexSearcher(borrowReader(staleOk));
		}

		public void returnSearcher(final IndexSearcher searcher)
				throws IOException {
			returnReader(searcher.getIndexReader());
		}

		public synchronized IndexReader borrowReader(final boolean staleOk)
				throws IOException {
			try {
				latch.await();
			} catch (final InterruptedException e) {
				// Ignored.
			}

			if (reader == null) {
				reader = writer.getReader();
				etag = newEtag();
				reader.incRef();
			}
			if (!staleOk) {
				reader.decRef();
				reader = writer.getReader();
				if (dirty) {
					etag = newEtag();
					dirty = false;
				}
			}
			reader.incRef();
			return reader;
		}

		public void returnReader(final IndexReader reader) throws IOException {
			reader.decRef();
		}

		private String newEtag() {
			return Long.toHexString(now());
		}
	}

	private static final long COMMIT_INTERVAL = SECONDS.toNanos(60);

	private boolean closed;

	private final HttpClient client;

	private Context context;

	private final Database database;

	private Logger logger;

	private final Map<String, View> paths = new HashMap<String, View>();

	private final Map<View, IndexState> states = Collections
			.synchronizedMap(new HashMap<View, IndexState>());

	private final File root;

	private long since;

	private long ddoc_seq;

	private UUID uuid;

	private long lastCommit;

	private HttpUriRequest req;
	
	private final CountDownLatch latch = new CountDownLatch(1);

	public DatabaseIndexer(final HttpClient client, final File root,
			final Database database) throws IOException {
		this.client = client;
		this.root = root;
		this.database = database;
	}

	public Void handleResponse(final HttpResponse response)
			throws ClientProtocolException, IOException {
		final HttpEntity entity = response.getEntity();
		final BufferedReader reader = new BufferedReader(new InputStreamReader(
				entity.getContent(), "UTF-8"));
		String line;
		loop: while ((line = reader.readLine()) != null) {
			maybeCommit();

			// Heartbeat.
			if (line.length() == 0) {
				logger.trace("heartbeat");
				continue loop;
			}

			final JSONObject json = JSONObject.fromObject(line);

			if (json.has("error")) {
				logger.warn("Indexing stopping due to error: " + json);
				break loop;
			}

			if (json.has("last_seq")) {
				logger.warn("End of changes detected.");
				break loop;
			}

			final long seq = json.getLong("seq");
			final String id = json.getString("id");
			CouchDocument doc;
			if (json.has("doc")) {
				doc = new CouchDocument(json.getJSONObject("doc"));
			} else {
				// include_docs=true doesn't work prior to 0.11.
				try {
					doc = database.getDocument(id);
				} catch (final HttpResponseException e) {
					switch (e.getStatusCode()) {
					case HttpStatus.SC_NOT_FOUND:
						doc = CouchDocument.deletedDocument(id);
						break;
					default:
						logger.warn("Failed to fetch " + id);
						break loop;
					}
				}
			}

			if (id.startsWith("_design") && seq > ddoc_seq) {
				logger.info("Exiting due to design document change.");
				break loop;
			}

			if (doc.isDeleted()) {
				for (final IndexState state : states.values()) {
					state.writer.deleteDocuments(new Term("_id", id));
					state.pending_seq = seq;
				}
			} else {
				for (final Entry<View, IndexState> entry : states.entrySet()) {
					final View view = entry.getKey();
					final IndexState state = entry.getValue();

					final Document[] docs;
					try {
						docs = state.converter.convert(doc, view
								.getDefaultSettings(), database);
					} catch (final Exception e) {
						logger.warn(id + " caused " + e.getMessage());
						continue loop;
					}

					state.writer.deleteDocuments(new Term("_id", id));
					for (final Document d : docs) {
						state.writer.addDocument(d, view.getAnalyzer());
					}
					state.pending_seq = seq;
					state.dirty = true;
				}
				releaseLatches();
			}
		}
		req.abort();
		return null;
	}

	private void releaseLatches() {
		for (final IndexState state : states.values()) {
			if (state.pending_seq >= ddoc_seq) {
				state.latch.countDown();
			}
		}
	}

	public void run() {
		if (closed) {
			throw new IllegalStateException("closed!");
		}

		try {
			init();
		} catch (final IOException e) {
			logger.warn("Exiting after init() raised I/O exception.", e);
			return;
		}

		try {
			try {
				req = database.getChangesRequest(since);
				logger.info("Indexing from update_seq " + since);
				client.execute(req, this);
			} finally {
				close();
			}
		} catch (final SocketException e) {
			// Ignored because req.abort() does this.
		} catch (final IOException e) {
			logger.warn("Exiting due to I/O exception.", e);
		}
	}

	public void search(final HttpServletRequest req,
			final HttpServletResponse resp) throws IOException {
		final IndexState state = getState(req);
		if (state.notModified(req)) {
			resp.setStatus(304);
			return;
		}
		final IndexSearcher searcher = state.borrowSearcher(isStaleOk(req));
		final String etag = state.getEtag();
		final JSONArray result = new JSONArray();
		try {
			for (final String queryString : req.getParameterValues("q")) {
				final Query q = state.parser.parse(queryString);
				final JSONObject queryRow = new JSONObject();
				queryRow.put("q", q.toString());
				if (getBooleanParameter(req, "debug")) {
					queryRow.put("plan", QueryPlan.toPlan(q));
				}
				queryRow.put("etag", etag);
				if (getBooleanParameter(req, "rewrite")) {
					final Query rewritten_q = q.rewrite(searcher
							.getIndexReader());
					queryRow.put("rewritten_q", rewritten_q.toString());

					final JSONObject freqs = new JSONObject();

					final Set<Term> terms = new HashSet<Term>();
					rewritten_q.extractTerms(terms);
					for (final Object term : terms) {
						final int freq = searcher.docFreq((Term) term);
						freqs.put(term, freq);
					}
					queryRow.put("freqs", freqs);
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
					final int max = Math.max(0, Math.min(td.totalHits - skip,
							limit));
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
						}// Include sort order (if any).
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
						// Fetch documents (if requested).
						if (include_docs && fetch_ids.length > 0) {
							database.getDocuments(fetch_ids);
							final List<CouchDocument> fetched_docs = database
									.getDocuments(fetch_ids);
							for (int j = 0; j < max; j++) {
								rows.getJSONObject(j).put("doc",
										fetched_docs.get(j).asJson());
							}

						}
						stopWatch.lap("fetch");

						queryRow.put("skip", skip);
						queryRow.put("limit", limit);
						queryRow.put("total_rows", td.totalHits);
						queryRow.put("search_duration", stopWatch
								.getElapsed("search"));
						queryRow.put("fetch_duration", stopWatch
								.getElapsed("fetch"));
						// Include sort info (if requested).
						if (td instanceof TopFieldDocs) {
							queryRow.put("sort_order", CustomQueryParser
									.toString(((TopFieldDocs) td).fields));
						}
					}
					result.add(queryRow);
				}
			}
		} catch (final ParseException e) {
			ServletUtils.sendJSONError(req, resp, 400, "Bad query syntax: "
					+ e.getMessage());
			return;
		} finally {
			state.returnSearcher(searcher);
		}

		resp.setHeader("ETag", etag);
		resp.setHeader("Cache-Control", "must-revalidate");
		negotiateContentType(req, resp);

		final JSON json = result.size() > 1 ? result : result.getJSONObject(0);
		final String callback = req.getParameter("callback");
		final String body;
		if (callback != null) {
			body = String.format("%s(%s)", callback, json);
		} else {
			body = json.toString(getBooleanParameter(req, "debug") ? 2 : 0);
		}

		final Writer writer = resp.getWriter();
		try {
			writer.write(body);
		} finally {
			writer.close();
		}
	}

	public void info(final HttpServletRequest req,
			final HttpServletResponse resp) throws IOException {
		final IndexState state = getState(req);
		final IndexReader reader = state.borrowReader(isStaleOk(req));
		try {
			final JSONObject result = new JSONObject();
			result.put("current", reader.isCurrent());
			result.put("disk_size", Utils.directorySize(reader.directory()));
			result.put("doc_count", reader.numDocs());
			result.put("doc_del_count", reader.numDeletedDocs());
			final JSONArray fields = new JSONArray();
			for (final Object field : reader.getFieldNames(FieldOption.INDEXED)) {
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
		} finally {
			state.returnReader(reader);
		}
	}

	private IndexState getState(final HttpServletRequest req)
			throws IOException {
		final String path = pathParts(req)[2] + "/" + pathParts(req)[3];
		final View view = paths.get(path);
		if (view == null) {
			return null;
		}
		return states.get(view);
	}

	private void init() throws IOException {
		this.logger = Logger.getLogger(DatabaseIndexer.class.getName() + "."
				+ database.getInfo().getName());
		this.uuid = database.getOrCreateUuid();

		this.context = Context.enter();
		context.setClassShutter(new RestrictiveClassShutter());
		context.setOptimizationLevel(9);

		this.ddoc_seq = database.getInfo().getUpdateSequence();
		this.since = 0;

		for (final DesignDocument ddoc : database.getAllDesignDocuments()) {
			for (final Entry<String, View> entry : ddoc.getAllViews()
					.entrySet()) {
				final String name = entry.getKey();
				final View view = entry.getValue();
				paths.put(ddoc.getId().substring(8) + "/" + name, view);

				if (!states.containsKey(view)) {
					final Directory dir = viewDir(view);
					final long seq = getUpdateSequence(dir);
					if (since == 0) {
						since = seq;
					}
					if (seq != -1L) {
						since = Math.min(since, seq);
					}

					final DocumentConverter converter = new DocumentConverter(
							context, view);
					final IndexWriter writer = newWriter(dir);
					final QueryParser parser = new CustomQueryParser(
							Constants.VERSION, Constants.DEFAULT_FIELD, view
									.getAnalyzer());

					states.put(view, new IndexState(converter, writer, parser, seq));
				}
			}
		}
		logger.debug("paths: " + paths);

		this.lastCommit = now();
		releaseLatches();
		latch.countDown();
	}
	
	public void awaitInitialization()  {
		try {
			latch.await();
		} catch (final InterruptedException e) {
			// Ignore.
		}
	}

	private void close() throws IOException {
		this.closed = true;

		for (IndexState state : states.values()) {
			state.close();
		}
		states.clear();
		Context.exit();
	}

	private void maybeCommit() throws IOException {
		if (now() - lastCommit >= COMMIT_INTERVAL) {
			commitAll();
		}
	}

	private Directory viewDir(final View view) throws IOException {
		final File uuidDir = new File(root, uuid.toString());
		final File viewDir = new File(uuidDir, view.getDigest());
		viewDir.mkdirs();
		return FSDirectory.open(viewDir);
	}

	private long getUpdateSequence(final Directory dir) throws IOException {
		if (!IndexReader.indexExists(dir)) {
			return 0L;
		}
		return getUpdateSequence(IndexReader.getCommitUserData(dir));
	}

	private long getUpdateSequence(final IndexWriter writer) throws IOException {
		return getUpdateSequence(writer.getDirectory());
	}

	private long getUpdateSequence(final Map<String, String> userData) {
		if (userData != null && userData.containsKey("last_seq")) {
			return Long.parseLong(userData.get("last_seq"));
		}
		return 0L;
	}

	private IndexWriter newWriter(final Directory dir) throws IOException {
		final IndexWriter result = new IndexWriter(dir, Constants.ANALYZER,
				MaxFieldLength.UNLIMITED);
		result.setMergeFactor(5);
		result.setUseCompoundFile(false);
		return result;
	}

	private void commitAll() throws IOException {
		for (final Entry<View, IndexState> entry : states.entrySet()) {
			final View view = entry.getKey();
			final IndexState state = entry.getValue();

			if (state.pending_seq > getUpdateSequence(state.writer)) {
				final Map<String, String> userData = new HashMap<String, String>();
				userData.put("last_seq", Long.toString(state.pending_seq));
				state.writer.commit(userData);
				logger.info(view + " now at update_seq " + state.pending_seq);
			}
		}
		lastCommit = now();
	}

	private boolean getBooleanParameter(final HttpServletRequest req,
			final String parameterName) {
		return Boolean.parseBoolean(req.getParameter(parameterName));
	}

	private int getIntParameter(final HttpServletRequest req,
			final String parameterName, final int defaultValue) {
		final String result = req.getParameter(parameterName);
		return result != null ? Integer.parseInt(result) : defaultValue;
	}

	private static long now() {
		return System.nanoTime();
	}

	private String[] pathParts(final HttpServletRequest req) {
		return req.getRequestURI().replaceFirst("/", "").split("/");
	}

	private boolean isStaleOk(final HttpServletRequest req) {
		return "ok".equals(req.getParameter("stale"));
	}

	private void negotiateContentType(final HttpServletRequest req,
			final HttpServletResponse resp) {
		final String accept = req.getHeader("Accept");
		if (getBooleanParameter(req, "force_json")
				|| (accept != null && accept.contains("application/json"))) {
			resp.setContentType("application/json");
		} else {
			resp.setContentType("text/plain");
		}
		if (!resp.containsHeader("Vary")) {
			resp.addHeader("Vary", "Accept");
		}
		resp.setCharacterEncoding("utf-8");
	}
}
