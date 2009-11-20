package com.github.rnewson.couchdb.lucene;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.tika.io.IOUtils;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;

import com.github.rnewson.couchdb.lucene.Lucene.ReaderCallback;
import com.github.rnewson.couchdb.lucene.Lucene.WriterCallback;
import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.util.Analyzers;
import com.github.rnewson.couchdb.lucene.util.Constants;
import com.github.rnewson.couchdb.lucene.util.StatusCodeResponseHandler;

public final class ViewIndexer implements Runnable {

    private static final long COMMIT_INTERVAL = SECONDS.toNanos(10);

    private final Logger logger;
    private HttpClient client;
    private Context context;
    private final IndexKey key;
    private final CouchDbRegistry registry;
    private Database database;
    private String url;
    private final Lucene lucene;

    public ViewIndexer(final Lucene lucene, final CouchDbRegistry registry, final IndexKey key) {
        this.lucene = lucene;
        this.logger = Logger.getLogger(key.toString());
        this.registry = registry;
        this.key = key;
    }

    public void run() {
        try {
            setup();
        } catch (final Exception e) {
            logger.warn("Exception starting indexing.", e);
            return;
        }
        try {
            index();
        } catch (final Exception e) {
            logger.warn("Exception while indexing.", e);
        } finally {
            teardown();
        }
    }

    private void setup() throws IOException {
        logger.info("Starting.");
        context = Context.enter();
        context.setClassShutter(new RestrictiveClassShutter());
        context.setOptimizationLevel(9);
        client = httpClient();
        url = registry.createUrlByHostKey(key.getHostKey(), key.getDatabaseName());
        final Couch couch = new Couch(client, registry.createUrlByHostKey(key.getHostKey(), ""));
        database = couch.getDatabase(key.getDatabaseName());
    }

    private void teardown() {
        logger.info("Stopping.");
        client.getConnectionManager().shutdown();
        Context.exit();
    }

    private void index() throws IOException {
        final UUID uuid = getDatabaseUuid();
        final JSONObject ddoc = database.getDocument("_design/" + key.getDesignDocumentName());
        new ViewChangesHandler(uuid, ddoc).start();
    }

    private HttpClient httpClient() {
        final HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUseExpectContinue(params, false);
        return new DefaultHttpClient(params);
    }

    private final class RestrictiveClassShutter implements ClassShutter {
        public boolean visibleToScripts(final String fullClassName) {
            return false;
        }
    }

    private JSONObject defaults() {
        final JSONObject result = new JSONObject();
        result.put("field", Constants.DEFAULT_FIELD);
        result.put("store", "no");
        result.put("index", "analyzed");
        result.put("type", "string");
        return result;
    }

    private UUID getDatabaseUuid() throws IOException {
        final HttpGet get = new HttpGet(url + "/_local/lucene");
        UUID result = client.execute(get, new UUIDHandler());

        if (result == null) {
            result = UUID.randomUUID();
            final String doc = String.format("{\"uuid\":\"%s\"}", result);
            final HttpPut put = new HttpPut(url + "/_local/lucene");
            put.setEntity(new StringEntity(doc));
            final int sc = client.execute(put, new StatusCodeResponseHandler());
            switch (sc) {
            case 201:
                break;
            case 404:
            case 409:
                result = getDatabaseUuid();
                break;
            default:
                throw new IOException("Unexpected error code: " + sc);
            }
        }

        logger.info("Database has uuid " + result);
        return result;
    }

    private class UUIDHandler implements ResponseHandler<UUID> {

        public UUID handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
            switch (response.getStatusLine().getStatusCode()) {
            case 200:
                final String body = IOUtils.toString(response.getEntity().getContent());
                final JSONObject json = JSONObject.fromObject(body);
                return UUID.fromString(json.getString("uuid"));
            default:
                return null;
            }
        }

    }

    private class ViewChangesHandler implements ResponseHandler<Void> {

        private long since;
        private final JSONObject defaults;
        private final Analyzer analyzer;
        private final DocumentConverter converter;
        private long pendingSince;
        private boolean pendingCommit;
        private final String digest;

        public ViewChangesHandler(final UUID uuid, final JSONObject ddoc) throws IOException {
            final JSONObject view = extractView(ddoc);
            defaults = view.has("defaults") ? view.getJSONObject("defaults") : defaults();
            analyzer = Analyzers.getAnalyzer(view.optString("analyzer", "standard"));
            final String function = extractFunction(ddoc);
            converter = new DocumentConverter(context, null, function);
            lucene.createWriter(key, uuid, function);
            digest = Lucene.digest(function);
            lucene.withReader(key, false, new ReaderCallback() {
                public void callback(final IndexReader reader) throws IOException {
                    final Map commit = reader.getCommitUserData();
                    if (commit != null && commit.containsKey("last_seq")) {
                        since = Long.parseLong((String) commit.get("last_seq"));
                    }
                }

                public void onMissing() throws IOException {
                    since = 0;
                }
            });
        }

        private JSONObject extractView(final JSONObject ddoc) {
            final JSONObject fulltext = ddoc.getJSONObject("fulltext");
            return fulltext.getJSONObject(key.getViewName());
        }

        private String extractFunction(final JSONObject ddoc) {
            return StringUtils.trim(extractView(ddoc).getString("index"));
        }

        public void start() throws IOException {
            database.getChanges(since, this);
        }

        public Void handleResponse(final HttpResponse response) throws IOException {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                commitDocuments();

                // Heartbeat.
                if (line.length() == 0) {
                    continue;
                }

                final JSONObject json = JSONObject.fromObject(line);

                if (json.has("error")) {
                    logger.warn("Indexing stopping due to error: " + json);
                    return null;
                }
                
                if (json.has("last_seq")) {
                    logger.warn("End of changes detected.");
                    return null;
                }

                final JSONObject doc = json.getJSONObject("doc");
                final String id = doc.getString("_id");
                if (id.equals("_design/" + key.getDesignDocumentName())) {
                    if (doc.optBoolean("_deleted")) {
                        logger.info("Design document for this view was deleted.");
                        return null;
                    }                    
                    final String newDigest = Lucene.digest(extractFunction(doc));
                    if (!digest.equals(newDigest)) {
                        logger.info("Digest of function changed.");
                        return null;
                    }
                } else if (id.startsWith("_design")) {
                    // Ignore other design document changes.
                    continue;
                } else if (doc.optBoolean("_deleted")) {
                    lucene.withWriter(key, new WriterCallback() {

                        public boolean callback(final IndexWriter writer) throws IOException {
                            writer.deleteDocuments(new Term("_id", id));
                            return true;
                        }

                        public void onMissing() throws IOException {
                            // Ignore.
                        }
                    });
                    setPendingCommit(true);
                } else {
                    final Document[] docs;
                    try {
                        docs = converter.convert(doc, defaults, database);
                    } catch (final RhinoException e) {
                        logger.warn(id + " caused " + e.getMessage());
                        continue;
                    }
                    logger.debug(id + " generated " + docs.length + " documents.");
                    lucene.withWriter(key, new WriterCallback() {

                        public boolean callback(final IndexWriter writer) throws IOException {
                            writer.deleteDocuments(new Term("_id", id));
                            for (final Document doc : docs) {
                                writer.addDocument(doc, analyzer);
                            }
                            return true;
                        }

                        public void onMissing() throws IOException {
                            // Ignore.
                        }
                    });
                    setPendingCommit(true);
                }
                since = json.getLong("seq");
            }
            return null;
        }

        private void commitDocuments() throws IOException {
            if (!hasPendingCommit())
                return;

            lucene.withWriter(key, new WriterCallback() {
                public boolean callback(final IndexWriter writer) throws IOException {
                    final Map<String, String> userData = new HashMap<String, String>();
                    userData.put("last_seq", Long.toString(since));
                    logger.info("Checkpoint at update_seq " + since);
                    writer.commit(userData);
                    return false;
                }

                public void onMissing() throws IOException {
                    // Ignore.
                }
            });

            setPendingCommit(false);
        }

        private boolean hasPendingCommit() {
            final boolean timeoutReached = (now() - pendingSince) >= COMMIT_INTERVAL;
            return pendingCommit && timeoutReached;
        }

        private void setPendingCommit(final boolean pendingCommit) {
            if (pendingCommit) {
                if (!this.pendingCommit) {
                    this.pendingCommit = true;
                    this.pendingSince = now();
                }
            } else {
                this.pendingCommit = false;
                this.pendingSince = 0L;
            }
        }

        private long now() {
            return System.nanoTime();
        }

    }

}
