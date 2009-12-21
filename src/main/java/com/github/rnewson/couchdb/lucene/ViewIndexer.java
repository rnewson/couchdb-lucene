package com.github.rnewson.couchdb.lucene;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;

import com.github.rnewson.couchdb.lucene.Lucene.ReaderCallback;
import com.github.rnewson.couchdb.lucene.Lucene.WriterCallback;
import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.util.Analyzers;
import com.github.rnewson.couchdb.lucene.util.Constants;
import com.github.rnewson.couchdb.lucene.util.Utils;

public final class ViewIndexer implements Runnable {

    private final class RestrictiveClassShutter implements ClassShutter {
        public boolean visibleToScripts(final String fullClassName) {
            return false;
        }
    }

    private class ViewChangesHandler implements ResponseHandler<Void> {

        private final Analyzer analyzer;
        private final DocumentConverter converter;
        private final JSONObject defaults;
        private final String digest;
        private HttpUriRequest request;
        private final long latchThreshold;
        private boolean pendingCommit;
        private long pendingSince;
        private long since;

        public ViewChangesHandler(final UUID uuid, final JSONObject view, final long latchThreshold) throws IOException {
            this.latchThreshold = latchThreshold;
            this.defaults = view.has("defaults") ? view.getJSONObject("defaults") : defaults();
            this.analyzer = Analyzers.getAnalyzer(view.optString("analyzer", "standard"));
            final String function = extractFunction(view);
            this.converter = new DocumentConverter(context, null, function);
            lucene.createWriter(path, uuid, function);
            this.digest = Lucene.digest(function);
            lucene.withReader(path, false, new ReaderCallback() {
                public void callback(final IndexReader reader) throws IOException {
                    final Map<String, String> commit = reader.getCommitUserData();
                    if (commit != null && commit.containsKey("last_seq")) {
                        since = Long.parseLong(commit.get("last_seq"));
                    }
                }

                public void onMissing() throws IOException {
                    since = 0;
                }
            });
            releaseCatch();
        }

        public Void handleResponse(final HttpResponse response) throws IOException {
            final HttpEntity entity = response.getEntity();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"));
            String line;
            loop: while ((line = reader.readLine()) != null) {
                commitDocuments();

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

                final JSONObject doc;
                if (json.has("doc")) {
                    doc = json.getJSONObject("doc");
                } else {
                    // include_docs=true doesn't work prior to 0.11.
                    doc = database.getDocument(json.getString("id"));
                }

                final String id = doc.getString("_id");
                if (id.equals("_design/" + Utils.getDesignDocumentName(path))) {
                    if (doc.optBoolean("_deleted")) {
                        logger.info("Design document for this view was deleted.");
                        break loop;
                    }
                    final JSONObject view = extractView(doc);
                    if (view == null) {
                        logger.info("View was deleted.");
                        break loop;
                    }
                    final String fun = extractFunction(view);
                    if (fun == null) {
                        logger.warn("View has no index function.");
                        break loop;
                    }
                    final String newDigest = Lucene.digest(fun);
                    if (!digest.equals(newDigest)) {
                        logger.info("Digest of function changed.");
                        break loop;
                    }
                } else if (id.startsWith("_design")) {
                    // Ignore other design document changes.
                    continue loop;
                } else if (doc.optBoolean("_deleted")) {
                    logger.trace(id + " deleted.");
                    lucene.withWriter(path, new WriterCallback() {

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
                    logger.trace(id + " inserted or updated.");
                    final Document[] docs;
                    try {
                        docs = converter.convert(doc, defaults, database);
                    } catch (final RhinoException e) {
                        logger.warn(id + " caused " + e.getMessage());
                        continue loop;
                    }

                    lucene.withWriter(path, new WriterCallback() {

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
                releaseCatch();
            }
            logger.info("_changes loop is exiting.");
            request.abort();
            return null;
        }

        public void start() throws IOException {
            request = database.getChangesRequest(since);
            client.execute(request, this);
        }

        private void commitDocuments() throws IOException {
            if (!hasPendingCommit()) {
                return;
            }

            lucene.withWriter(path, new WriterCallback() {
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

        private long now() {
            return System.nanoTime();
        }

        private void releaseCatch() {
            if (since >= latchThreshold) {
                latch.countDown();
            }
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

    }

    private static final long COMMIT_INTERVAL = SECONDS.toNanos(10);
    private HttpClient client;
    private Context context;
    private Database database;
    private final CountDownLatch latch = new CountDownLatch(1);

    private final Logger logger;
    private final Lucene lucene;

    private final String path;

    private final boolean staleOk;

    public ViewIndexer(final Lucene lucene, final String path, final boolean staleOk) {
        this.lucene = lucene;
        this.logger = Logger.getLogger(ViewIndexer.class.getName() + "." + path);
        this.path = path;
        this.staleOk = staleOk;
    }

    public void awaitInitialIndexing() {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            // Ignore.
        }
    }

    public void run() {
        try {
            try {
                setup();
            } catch (final Exception e) {
                logger.warn("Exception while starting indexing.", e);
                return;
            }
            index();
        } catch (final Exception e) {
            logger.debug("Exception while indexing.", e);
        } finally {
            teardown();
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

    private String extractFunction(final JSONObject view) {
        if (!view.has("index")) {
            return null;
        }
        return StringUtils.trim(view.getString("index"));
    }

    private JSONObject extractView(final JSONObject ddoc) {
        if (!ddoc.has("fulltext")) {
            return null;
        }
        final JSONObject fulltext = ddoc.getJSONObject("fulltext");
        if (!fulltext.has(Utils.getViewName(path))) {
            return null;
        }
        return fulltext.getJSONObject(Utils.getViewName(path));
    }

    private UUID getDatabaseUuid() throws IOException {
        try {
            final JSONObject local = database.getDocument("_local/lucene");
            final UUID uuid = UUID.fromString(local.getString("uuid"));
            logger.trace("Database has uuid " + uuid);
            return uuid;
        } catch (final HttpResponseException e) {
            switch (e.getStatusCode()) {
            case HttpStatus.SC_NOT_FOUND:
                final UUID uuid = UUID.randomUUID();
                database.saveDocument("_local/lucene", String.format("{\"uuid\":\"%s\"}", uuid));
                return getDatabaseUuid();
            default:
                throw e;
            }
        }
    }

    private void index() throws IOException {
        final UUID uuid = getDatabaseUuid();
        final JSONObject ddoc = database.getDocument("_design/" + Utils.getDesignDocumentName(path));
        final JSONObject view = extractView(ddoc);
        if (view == null) {
            return;
        }
        if (extractFunction(view) == null)
            return;

        final JSONObject info = database.getInfo();
        final long seqThreshhold = staleOk ? 0 : info.getLong("update_seq");
        new ViewChangesHandler(uuid, view, seqThreshhold).start();
    }

    private void setup() throws IOException {
        logger.info("Starting.");
        context = Context.enter();
        context.setClassShutter(new RestrictiveClassShutter());
        context.setOptimizationLevel(9);
        client = HttpClientFactory.getInstance();
        final String url = String.format("http://%s:%d/", Utils.getHost(path), Utils.getPort(path));
        final Couch couch = Couch.getInstance(client, url);
        database = couch.getDatabase(Utils.getDatabase(path));
    }

    private void teardown() {
        latch.countDown();
        logger.info("Stopping.");
        client.getConnectionManager().shutdown();
        Context.exit();
    }

}
