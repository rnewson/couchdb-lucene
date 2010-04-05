package com.github.rnewson.couchdb.lucene;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;

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

public final class DatabaseIndexer implements ResponseHandler<Void> {

	public static void main(String[] args) throws Exception {
		final HttpClient client = HttpClientFactory.getInstance();
		final Couch couch = Couch.getInstance(client, "http://localhost:5984");
		final Database db = couch.getDatabase("db1");
		final DatabaseIndexer indexer = new DatabaseIndexer(client, new File(
				"target/tmp"), db);
		indexer.index();
	}

	private final class RestrictiveClassShutter implements ClassShutter {
		public boolean visibleToScripts(final String fullClassName) {
			return false;
		}
	}

	private static class IndexState {
		private long seq;
		private DocumentConverter converter;
		private IndexWriter writer;
		private IndexReader reader;

		private void close() throws IOException {
			if (reader != null)
				reader.close();
			if (writer != null)
				writer.rollback();
		}
	}

	private static final long COMMIT_INTERVAL = SECONDS.toNanos(5);

	private final HttpClient client;

	private Context context;

	private final Database database;

	private Logger logger;

	private final Map<View, IndexState> states = new HashMap<View, IndexState>();

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

	private Set<View> getCurrentViews() throws IOException {
		final Set<View> result = new HashSet<View>();
		for (final DesignDocument ddoc : database.getAllDesignDocuments()) {
			result.addAll(ddoc.getAllViews());
		}
		return result;
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
					state.seq = seq;
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
					state.seq = seq;
				}
			}
		}
		return null;
	}

	public void index() throws IOException {
		init();
		try {
			final HttpUriRequest req = database.getChangesRequest(since);
			logger.info("Indexing from update_seq " + since);
			client.execute(req, this);
		} finally {
			close();
		}
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
		for (final View view : getCurrentViews()) {
			final Directory dir = viewDir(view);
			final long seq = getUpdateSequence(dir);
			if (since == 0) {
				since = seq;
			}
			if (seq != -1L) {
				since = Math.min(since, seq);
			}

			if (!states.containsKey(view)) {
				final IndexState state = new IndexState();
				state.converter = new DocumentConverter(context, view);
				state.writer = newWriter(dir);
				states.put(view, state);
			}
		}

		this.lastCommit = now();
	}

	private void close() throws IOException {
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
		logger.info("Committing recent changes to disk.");
		for (final IndexState state : states.values()) {
			if (state.seq > getUpdateSequence(state.writer)) {
				final Map<String, String> userData = new HashMap<String, String>();
				userData.put("last_seq", Long.toString(state.seq));
				state.writer.commit(userData);
			}
		}
		lastCommit = now();
	}

	private long now() {
		return System.nanoTime();
	}

}
