package com.github.rnewson.couchdb.lucene;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;

import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.CouchDocument;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.couchdb.DesignDocument;
import com.github.rnewson.couchdb.lucene.couchdb.View;
import com.github.rnewson.couchdb.lucene.util.Constants;

public final class DatabaseIndexer implements Runnable, ResponseHandler<Void> {

	public static void main(String[] args) throws Exception {
		final HttpClient client = HttpClientFactory.getInstance();
		final Couch couch = Couch.getInstance(client, "http://localhost:5984");
		final Database db = couch.getDatabase("db1");
		final DatabaseIndexer indexer = new DatabaseIndexer(client, new File(
				"target/tmp"), db);
		indexer.init();
	}

	private final class RestrictiveClassShutter implements ClassShutter {
		public boolean visibleToScripts(final String fullClassName) {
			return false;
		}
	}

	public static class IndexState {

		private final DocumentConverter converter;
		private final IndexWriter writer;
		private final QueryParser parser;
		private final Database database;

		private long pending_seq;
		private String etag;
		private IndexReader reader;

		public IndexState(final DocumentConverter converter,
				final IndexWriter writer, final QueryParser parser,
				final Database database) {
			this.converter = converter;
			this.writer = writer;
			this.parser = parser;
			this.database = database;
		}

		public synchronized boolean notModified(final HttpServletRequest req) {
			return etag.equals(req.getHeader("If-None-Match"));
		}

		public synchronized String getEtag() {
			return etag;
		}

		public QueryParser getParser() {
			return parser;
		}
		
		public Database getDatabase() {
			return database;
		}

		private synchronized void close() throws IOException {
			if (reader != null)
				reader.close();
			if (writer != null)
				writer.rollback();
		}

		public synchronized IndexSearcher borrowSearcher(final boolean staleOk)
				throws IOException {
			if (reader == null) {
				reader = writer.getReader();
				etag = newEtag();
				reader.incRef();
			}
			if (!staleOk) {
				final IndexReader newReader = reader.reopen();
				if (newReader != reader) {
					reader.decRef();
					reader = newReader;
					etag = newEtag();
				}
			}
			reader.incRef();
			return new IndexSearcher(reader);
		}

		public void returnSearcher(final IndexSearcher searcher)
				throws IOException {
			searcher.getIndexReader().decRef();
		}

		private String newEtag() {
			return Long.toHexString(now());
		}
	}

	private static final long COMMIT_INTERVAL = SECONDS.toNanos(5);

	private boolean initialized, closed;

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
				}
			}
		}
		return null;
	}

	public void run() {
		if (!initialized) {
			throw new IllegalStateException("not initialized.");
		}

		if (closed) {
			throw new IllegalStateException("closed!");
		}

		try {
			try {
				final HttpUriRequest req = database.getChangesRequest(since);
				logger.info("Indexing from update_seq " + since);
				client.execute(req, this);
			} finally {
				close();
			}
		} catch (final IOException e) {
			logger.warn("Exiting due to I/O exception.", e);
		}
	}

	public IndexState getState(final String ddocName, final String viewName)
			throws IOException {
		final String path = ddocName + "/" + viewName;
		final View view = paths.get(path);
		if (view == null) {
			return null;
		}
		return states.get(view);
	}

	public void init() throws IOException {
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

					states.put(view, new IndexState(converter, writer, parser, database));
				}
			}
		}
		logger.debug("paths: " + paths);

		this.lastCommit = now();
		this.initialized = true;
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

	private static long now() {
		return System.nanoTime();
	}

}
