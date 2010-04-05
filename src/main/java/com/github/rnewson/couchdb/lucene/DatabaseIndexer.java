package com.github.rnewson.couchdb.lucene;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

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

	private static class IndexState {
		private long seq;
		private IndexWriter writer;
		private IndexReader reader;
	}

	private final HttpClient client;

	private final Database database;

	private Logger logger;

	private final Map<View, IndexState> states = new HashMap<View, IndexState>();

	private final File root;

	private long since;

	private UUID uuid;

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

			if (id.startsWith("_design")) {
				refresh();
			}
		}
		return null;
	}

	public void index() throws IOException {
		this.logger = Logger.getLogger(DatabaseIndexer.class.getName() + "."
				+ database.getInfo().getName());
		this.uuid = database.getOrCreateUuid();
		refresh();
		final HttpUriRequest req = database.getChangesRequest(since);
		logger.info("Indexing from update_seq " + since);
		client.execute(req, this);
	}

	private void maybeCommit() {

	}

	/**
	 * A design document has changed therefore we must refresh our view of the
	 * database. This includes;
	 * <ul>
	 * <li>calculating the lowest update_seq of any fulltext view
	 * <li>closing any index reader and writer that cannot be reached
	 * <li>open an index writer for all views
	 * </ul>
	 * 
	 * @throws IOException
	 */
	private void refresh() throws IOException {
		since = 0;

		final Set<View> currentViews = getCurrentViews();

		// Close index state for any non-current view.
		final Iterator<Entry<View, IndexState>> it = states.entrySet()
				.iterator();
		while (it.hasNext()) {
			final Entry<View, IndexState> entry = it.next();
			final View view = entry.getKey();
			final IndexState state = entry.getValue();

			if (!currentViews.contains(view)) {
				it.remove();
				state.reader.close();
				state.writer.rollback();
			}
		}

		// Ensure we have index state for every current view.
		for (final View view : currentViews) {
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
				state.writer = newWriter(dir);
				state.seq = seq;
				states.put(view, state);
			}
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

		final Map<String, String> userData = IndexReader.getCommitUserData(dir);
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

}
