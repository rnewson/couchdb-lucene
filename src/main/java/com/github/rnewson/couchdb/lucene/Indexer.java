package com.github.rnewson.couchdb.lucene;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.mortbay.component.AbstractLifeCycle;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import com.github.rnewson.couchdb.lucene.LuceneGateway.ReaderCallback;
import com.github.rnewson.couchdb.lucene.LuceneGateway.WriterCallback;
import com.github.rnewson.couchdb.lucene.RhinoDocument.RhinoContext;
import com.github.rnewson.couchdb.lucene.util.Analyzers;

/**
 * Pull data from couchdb into Lucene indexes.
 * 
 * @author robertnewson
 */
public final class Indexer extends AbstractLifeCycle {

    private static final long POLL_TIMEOUT = SECONDS.toMillis(30);

    private static final long COMMIT_INTERVAL = SECONDS.toNanos(60);

    private State state;

    private final Set<String> activeTasks = new HashSet<String>();

    private ExecutorService executor;

    private ScheduledExecutorService scheduler;

    public Indexer(final State state) {
        this.state = state;
    }

    @Override
    protected void doStart() throws Exception {
        executor = Executors.newCachedThreadPool();
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(new CouchPoller(), 0, 60, TimeUnit.SECONDS);
    }

    @Override
    protected void doStop() throws Exception {
        scheduler.shutdownNow();
        executor.shutdownNow();
    }

    private class CouchPoller implements Runnable {

        private final Logger logger = Logger.getLogger(CouchPoller.class);

        public void run() {
            try {
                final String[] databases = state.couch.getAllDatabases();
                synchronized (activeTasks) {
                    for (final String databaseName : databases) {
                        if (!activeTasks.contains(databaseName)) {
                            activeTasks.add(databaseName);
                            executor.execute(new DatabasePuller(databaseName));
                        }
                    }
                }
            } catch (final HttpException e) {
                // Ignore.
            } catch (final IOException e) {
                // Ignore.
            }
        }
    }

    private static class ViewTuple {
        private final JSONObject defaults;
        private final Analyzer analyzer;
        private final Function function;

        public ViewTuple(final JSONObject defaults, final Analyzer analyzer, final Function function) {
            this.defaults = defaults;
            this.analyzer = analyzer;
            this.function = function;
        }
    }

    private class DatabasePuller implements Runnable {

        private final Logger logger;

        private final String databaseName;

        private long since = 0L;

        private Context context;

        private ScriptableObject scope;

        private Function main;

        private boolean pendingCommit;

        private long pendingSince;

        private final Map<ViewSignature, ViewTuple> functions = new HashMap<ViewSignature, ViewTuple>();

        public DatabasePuller(final String databaseName) {
            logger = Utils.getLogger(DatabasePuller.class, databaseName);
            this.databaseName = databaseName;
        }

        public void run() {
            logger.debug("Tracking begins");
            try {
                enterContext();
                final boolean isLuceneEnabled = mapAllDesignDocuments();
                if (!isLuceneEnabled) {
                    logger.debug("No fulltext functions found.");
                    return;
                }
                readCheckpoints();
                while (isRunning() && updateIndexes())
                    ;
            } catch (final Exception e) {
                logger.warn("Tracking interrupted by exception.", e);
            } finally {
                leaveContext();
                untrack();
            }
        }

        private void enterContext() throws Exception {
            context = ContextFactory.getGlobal().enterContext();
            // Optimize as much as possible.
            context.setOptimizationLevel(9);
            // Security restrictions
            context.setClassShutter(new RestrictiveClassShutter());
            // Setup.
            scope = context.initStandardObjects();
            // Allow custom document helper class.
            ScriptableObject.defineClass(scope, RhinoDocument.class);
            // Load JSON parser.
            context.evaluateString(scope, loadResource("json2.js"), "json2", 0, null);
            // Define outer function.
            main = context.compileFunction(scope, "function(json, func){return func(JSON.parse(json));}", "main", 0, null);
        }

        private String loadResource(final String name) throws IOException {
            final InputStream in = Indexer.class.getClassLoader().getResourceAsStream(name);
            try {
                return IOUtils.toString(in, "UTF-8");
            } finally {
                in.close();
            }
        }

        private boolean mapAllDesignDocuments() throws IOException {
            final JSONArray designDocuments = state.couch.getAllDesignDocuments(databaseName);
            boolean isLuceneEnabled = false;
            for (int i = 0; i < designDocuments.size(); i++) {
                isLuceneEnabled |= mapDesignDocument(designDocuments.getJSONObject(i).getJSONObject("doc"));
            }
            return isLuceneEnabled;
        }

        private void readCheckpoints() throws IOException {
            long since = Long.MAX_VALUE;
            for (final ViewSignature sig : functions.keySet()) {
                since = Math.min(since, state.lucene.withReader(sig, false, new ReaderCallback<Long>() {
                    public Long callback(final IndexReader reader) throws IOException {
                        final Map<String, String> commitUserData = reader.getCommitUserData();
                        final String result = commitUserData.get("update_seq");
                        return result != null ? Long.parseLong(result) : 0L;
                    }
                }));
            }
            this.since = since;
            logger.debug("Existing indexes at update_seq " + since);
        }

        private boolean mapDesignDocument(final JSONObject designDocument) {
            final String designDocumentName = designDocument.getString("_id").substring(8);
            final JSONObject fulltext = designDocument.getJSONObject("fulltext");
            boolean isLuceneEnabled = false;
            if (fulltext != null) {
                for (final Object obj : fulltext.keySet()) {
                    final String viewName = (String) obj;
                    final JSONObject viewValue = fulltext.getJSONObject(viewName);
                    final JSONObject defaults = viewValue.has("defaults") ? viewValue.getJSONObject("defaults") : defaults();
                    final Analyzer analyzer = Analyzers.getAnalyzer(viewValue.optString("analyzer", "standard"));
                    String function = viewValue.getString("index");
                    function = function.replaceFirst("^\"", "");
                    function = function.replaceFirst("\"$", "");
                    final ViewSignature sig = state.locator
                            .update(databaseName, designDocumentName, viewName, viewValue.toString());
                    functions.put(sig, new ViewTuple(defaults, analyzer, context
                            .compileFunction(scope, function, viewName, 0, null)));
                    isLuceneEnabled = true;     
                }
            }
            return isLuceneEnabled;
        }

        private JSONObject defaults() {
            final JSONObject result = new JSONObject();
            result.put("field", Constants.DEFAULT_FIELD);
            result.put("store", "no");
            result.put("index", "analyzed");
            result.put("type", "string");
            return result;
        }

        /**
         * @return true if the indexing loop should continue, false to make it
         *         exit.
         */
        private boolean updateIndexes() throws IOException {
            final String url = state.couch.url(String.format("%s/_changes?" + "feed=continuous&" + "since=%d&"
                    + "include_docs=true&" + "timeout=" + POLL_TIMEOUT, databaseName, since));
            return state.httpClient.execute(new HttpGet(url), new ChangesResponseHandler());
        }

        private void untrack() {
            synchronized (activeTasks) {
                activeTasks.remove(databaseName);
            }
            logger.debug("Tracking ends");
        }

        private void leaveContext() {
            Context.exit();
        }

        private long now() {
            return System.nanoTime();
        }

        private final class RestrictiveClassShutter implements ClassShutter {
            public boolean visibleToScripts(final String fullClassName) {
                return false;
            }
        }

        private final class ChangesResponseHandler implements ResponseHandler<Boolean> {
            public Boolean handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                final HttpEntity entity = response.getEntity();
                final BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    final JSONObject json = JSONObject.fromObject(line);

                    // Error.
                    if (json.has("error")) {
                        if (json.optString("reason").equals("no_db_file")) {
                            logger.warn("Database deleted.");
                            // TODO delete indexes.
                        } else {
                            logger.warn("Unexpected error: " + json);
                        }
                        return false;
                    }

                    // End of feed.
                    if (json.has("last_seq")) {
                        if (hasPendingCommit(true)) {
                            commitDocuments();
                        }
                        return true;
                    }

                    // Time's up.
                    if (hasPendingCommit(false)) {
                        commitDocuments();
                    }

                    final JSONObject doc = json.getJSONObject("doc");
                    final String id = doc.getString("_id");

                    // New, updated or deleted document.
                    if (json.getString("id").startsWith("_design")) {
                        if (logger.isTraceEnabled())
                            logger.trace(id + ": design document updated.");
                        mapDesignDocument(doc);
                    } else if (doc.optBoolean("_deleted")) {
                        if (logger.isTraceEnabled())
                            logger.trace(id + ": document deleted.");
                        deleteDocument(doc);
                    } else {
                        // New or updated document.
                        if (logger.isTraceEnabled())
                            logger.trace(id + ": new/updated document.");
                        updateDocument(doc);
                    }

                    // Remember progress.
                    since = json.getLong("seq");
                }
                return true;
            }

            private void deleteDocument(final JSONObject doc) throws IOException {
                for (final ViewSignature sig : functions.keySet()) {
                    state.lucene.withWriter(sig, new WriterCallback<Void>() {
                        public Void callback(final IndexWriter writer) throws IOException {
                            writer.deleteDocuments(new Term("_id", doc.getString("_id")));
                            setPendingCommit(true);
                            return null;
                        }
                    });
                }
            }

            private void commitDocuments() throws IOException {
                final JSONObject tracker = fetchTrackingDocument(databaseName);
                tracker.put("update_seq", since);
                for (final ViewSignature sig : functions.keySet()) {
                    // Fetch or generate index uuid.
                    final String uuid = state.lucene.withReader(sig, true, new ReaderCallback<String>() {
                        public String callback(final IndexReader reader) throws IOException {
                            final String result = (String) reader.getCommitUserData().get("uuid");
                            return result != null ? result : UUID.randomUUID().toString();
                        }
                    });
                    tracker.put(sig.toString(), uuid);
                    // Tell Lucene.
                    state.lucene.withWriter(sig, new WriterCallback<Void>() {
                        public Void callback(final IndexWriter writer) throws IOException {
                            final Map<String, String> commitUserData = new HashMap<String, String>();
                            commitUserData.put("update_seq", Long.toString(since));
                            commitUserData.put("uuid", uuid);
                            logger.debug("Committing changes to " + sig + " with " + commitUserData);
                            // commit data is not written if there are no
                            // documents.
                            if (writer.maxDoc() == 0) {
                                writer.addDocument(new Document());
                            }
                            writer.commit(commitUserData);
                            return null;
                        }
                    });
                }
                // Tell Couch.
                state.couch.saveDocument(databaseName, "_local/lucene", tracker.toString());
                setPendingCommit(false);
            }

            private void updateDocument(final JSONObject doc) {
                for (final Entry<ViewSignature, ViewTuple> entry : functions.entrySet()) {
                    final RhinoContext rhinoContext = new RhinoContext();
                    rhinoContext.analyzer = entry.getValue().analyzer;
                    rhinoContext.databaseName = databaseName;
                    rhinoContext.defaults = entry.getValue().defaults;
                    rhinoContext.documentId = doc.getString("_id");
                    rhinoContext.state = state;
                    try {
                        final Object result = main.call(context, scope, null, new Object[] { doc.toString(),
                                entry.getValue().function });
                        if (result == null || result instanceof Undefined) {
                            return;
                        }
                        state.lucene.withWriter(entry.getKey(), new WriterCallback<Void>() {
                            public Void callback(final IndexWriter writer) throws IOException {
                                if (result instanceof RhinoDocument) {
                                    ((RhinoDocument) result).addDocument(rhinoContext, writer);
                                } else if (result instanceof NativeArray) {
                                    final NativeArray array = (NativeArray) result;
                                    for (int i = 0; i < (int) array.getLength(); i++) {
                                        if (array.get(i, null) instanceof RhinoDocument) {
                                            ((RhinoDocument) array.get(i, null)).addDocument(rhinoContext, writer);
                                        }
                                    }
                                }
                                return null;
                            }
                        });
                        setPendingCommit(true);
                    } catch (final RhinoException e) {
                        logger.warn("doc '" + doc.getString("id") + "' caused exception.", e);
                    } catch (final IOException e) {
                        logger.warn("doc '" + doc.getString("id") + "' caused exception.", e);
                    }
                }
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

        private boolean hasPendingCommit(final boolean ignoreTimeout) {
            if (ignoreTimeout)
                return pendingCommit;
            if (!pendingCommit)
                return false;
            return (now() - pendingSince) >= COMMIT_INTERVAL;
        }
    }

    private JSONObject fetchTrackingDocument(final String databaseName) throws IOException {
        try {
            return state.couch.getDoc(databaseName, "_local/lucene");
        } catch (final HttpResponseException e) {
            if (e.getStatusCode() == 404) {
                return new JSONObject();
            }
            throw e;
        }
    }

}
