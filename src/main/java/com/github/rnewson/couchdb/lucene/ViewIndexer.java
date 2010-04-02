package com.github.rnewson.couchdb.lucene;

/**
 * Copyright 2009 Robert Newson
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

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import net.sf.json.JSONObject;

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

import com.github.rnewson.couchdb.lucene.Lucene.ReaderCallback;
import com.github.rnewson.couchdb.lucene.Lucene.WriterCallback;
import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.CouchDocument;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.couchdb.DesignDocument;
import com.github.rnewson.couchdb.lucene.couchdb.View;
import com.github.rnewson.couchdb.lucene.util.IndexPath;

public final class ViewIndexer implements Runnable {

    private final class RestrictiveClassShutter implements ClassShutter {
        public boolean visibleToScripts(final String fullClassName) {
            return false;
        }
    }

    private class ViewChangesHandler implements ResponseHandler<Void> {

        private final View view;
        private final DocumentConverter converter;
        private HttpUriRequest request;
        private final long latchThreshold;
        private boolean pendingCommit;
        private long pendingSince;
        private long since;

        private long firstUpdate = -1;
        private long lastUpdate = -1;
        private long updateCount;

        public ViewChangesHandler(final UUID uuid, final View view, final long latchThreshold) throws IOException {
            this.latchThreshold = latchThreshold;
            this.view = view;
            this.converter = new DocumentConverter(context, view);
            lucene.createWriter(path, uuid, view);

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

        public Void handleResponse(final HttpResponse response) {
            try {
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

                    if (id.equals("_design/" + path.getDesignDocumentName())) {
                        final DesignDocument ddoc = new DesignDocument(doc);
                        if (ddoc.isDeleted()) {
                            logger.info("Design document for this view was deleted.");
                            break loop;
                        }

                        final View view = ddoc.getView(path.getViewName());
                        if (view == null) {
                            logger.info("View was deleted.");
                            break loop;
                        }

                        if (!this.view.equals(view)) {
                            logger.info("View changed.");
                            break loop;
                        }
                    } else if (id.startsWith("_design")) {
                        // Ignore other design document changes.
                    } else if (doc.isDeleted()) {
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
                        logger.trace(id + " updated.");
                        final Document[] docs;
                        try {
                            docs = converter.convert(doc, view.getDefaultSettings(), database);
                        } catch (final Exception e) {
                            logger.warn(id + " caused " + e.getMessage());
                            continue loop;
                        }

                        lucene.withWriter(path, new WriterCallback() {

                            public boolean callback(final IndexWriter writer) throws IOException {
                                writer.deleteDocuments(new Term("_id", id));
                                for (final Document doc : docs) {
                                    if (logger.isTraceEnabled()) {
                                        logger.trace(id + " -> " + doc);
                                    }
                                    writer.addDocument(doc, view.getAnalyzer());
                                }
                                return true;
                            }

                            public void onMissing() throws IOException {
                                // Ignore.
                            }
                        });
                        if (docs.length > 0) {
                            setPendingCommit(true);
                        }
                    }
                    since = json.getLong("seq");
                    updateCount++;
                    if (firstUpdate == -1)
                        firstUpdate = now();
                    lastUpdate = now();
                    releaseCatch();
                }
            } catch (final IOException e) {
                logger.warn("I/O exception inside response handler.", e);
            } finally {
                logger.info("_changes loop is exiting.");
                request.abort();
            }
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
                    logger.debug("Starting checkpoint at update_seq " + since);
                    writer.commit(userData);
                    final long elapsedNanos = lastUpdate - firstUpdate;
                    logger.info(String.format(
                            "Committed checkpoint at update_seq %,d (%,d updates in %d seconds, %,d updates per minute)", since,
                            updateCount, NANOSECONDS.toSeconds(elapsedNanos), (60000000000L * updateCount) / elapsedNanos));
                    updateCount = 0;
                    firstUpdate = -1;
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

    private static final long COMMIT_INTERVAL = SECONDS.toNanos(60);
    private HttpClient client;
    private Context context;
    private Database database;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final Logger logger;
    private final Lucene lucene;
    private final IndexPath path;
    private final boolean staleOk;
    private ViewChangesHandler handler;

    public ViewIndexer(final Lucene lucene, final IndexPath path, final boolean staleOk) {
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
        } catch (final SocketException e) {
            // Ignore.
        } catch (final Exception e) {
            logger.warn("Exception while indexing.", e);
        } finally {
            teardown();
        }
    }

    private void index() throws IOException {
        UUID uuid = database.getUuid();
        if (uuid == null) {
            database.createUuid();
            uuid = database.getUuid();
        }

        final DesignDocument ddoc = database.getDesignDocument(path.getDesignDocumentName());
        final View view = ddoc.getView(path.getViewName());
        if (view == null) {
            return;
        }

        final JSONObject info = database.getInfo();
        final long seqThreshhold = staleOk ? 0 : info.getLong("update_seq");
        this.handler = new ViewChangesHandler(uuid, view, seqThreshhold);
        handler.start();
    }

    private void setup() throws IOException {
        logger.info("Starting.");
        context = Context.enter();
        context.setClassShutter(new RestrictiveClassShutter());
        context.setOptimizationLevel(9);
        client = HttpClientFactory.getInstance();
        final Couch couch = Couch.getInstance(client, path.getUrl());
        database = couch.getDatabase(path.getDatabase());
    }

    private void teardown() {
        latch.countDown();
        logger.info("Stopping.");
        client.getConnectionManager().shutdown();
        Context.exit();
    }

}
