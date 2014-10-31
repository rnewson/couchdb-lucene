/*
 * Copyright Robert Newson
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

package com.github.rnewson.couchdb.lucene;

import com.github.rnewson.couchdb.lucene.couchdb.*;
import com.github.rnewson.couchdb.lucene.util.*;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParser.Operator;
import org.apache.lucene.search.*;
import org.apache.lucene.search.vectorhighlight.FastVectorHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.SingleInstanceLockFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.SocketException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;

import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class DatabaseIndexer implements Runnable, ResponseHandler<Void> {

    private class IndexState {

        private final DocumentConverter converter;
        private boolean readerDirty;
        private String etag;

        private final Analyzer analyzer;
        private UpdateSequence pending_seq;
        private DirectoryReader reader;
        private final IndexWriter writer;
        private final Database database;
        private final View view;

        public IndexState(final DocumentConverter converter,
                          final IndexWriter writer, final Analyzer analyzer,
                          final Database database, final View view) {
            this.converter = converter;
            this.writer = writer;
            this.analyzer = analyzer;
            this.database = database;
            this.view = view;
        }

        public synchronized DirectoryReader borrowReader(final boolean staleOk)
                throws IOException, JSONException {
            blockForLatest(staleOk);
            if (reader == null) {
                etag = newEtag();
            }

            if (reader != null) {
                reader.decRef();
            }
            reader = DirectoryReader.open(writer, !staleOk);
            if (readerDirty) {
                etag = newEtag();
                readerDirty = false;
            }

            reader.incRef();
            return reader;
        }

        public IndexSearcher borrowSearcher(final boolean staleOk)
                throws IOException, JSONException {
            return new IndexSearcher(borrowReader(staleOk));
        }

        public void returnReader(final IndexReader reader) throws IOException {
            reader.decRef();
        }

        public void returnSearcher(final IndexSearcher searcher)
                throws IOException {
            returnReader(searcher.getIndexReader());
        }

        public Query parse(final String query, final Operator operator, final Analyzer analyzer) throws ParseException, JSONException {
            final QueryParser parser = new CustomQueryParser(Constants.VERSION,
                    Constants.DEFAULT_FIELD, analyzer);
            parser.setDefaultOperator(operator);
            parser.setAllowLeadingWildcard(ini.getBoolean("lucene.allowLeadingWildcard", false));
            parser.setLowercaseExpandedTerms(ini.getBoolean("lucene.lowercaseExpandedTerms", true));
            return parser.parse(query);
        }

        public Analyzer analyzer(final String analyzerName) throws JSONException {
            return analyzerName == null ? this.analyzer : Analyzers.getAnalyzer(analyzerName);
        }

        private synchronized void close() throws IOException {
            if (reader != null)
                reader.close();
            if (writer != null)
                writer.rollback();
        }

        private synchronized String getEtag() {
            return etag;
        }

        public UUID getUuid() throws JSONException, IOException {
            return database.getUuid();
        }

        public String getDigest() {
            return view.getDigest();
        }

        private String newEtag() {
            return Long.toHexString(now());
        }

        private synchronized boolean notModified(final HttpServletRequest req) {
            return etag != null && etag.equals(req.getHeader("If-None-Match"));
        }

        private void blockForLatest(final boolean staleOk) throws IOException, JSONException {
            if (staleOk) {
                return;
            }
            final UpdateSequence latest = database.getLastSequence();
            synchronized (this) {
                long timeout = getSearchTimeout();
                while (pending_seq.isEarlierThan(latest)) {
                    try {
                        final long start = System.currentTimeMillis();
                        wait(timeout);
                        timeout -= (System.currentTimeMillis() - start);
                        if (timeout <= 0) {
                            throw new IOException("Search timed out.");
                        }
                    } catch (final InterruptedException e) {
                        throw new IOException("Search timed out.");
                    }
                }
            }
        }

        private synchronized void setPendingSequence(final UpdateSequence seq) {
            pending_seq = seq;
            notifyAll();
        }

        @Override
        public String toString() {
            return writer.getDirectory().toString();
        }
    }

    private final class RestrictiveClassShutter implements ClassShutter {

        public boolean visibleToScripts(final String fullClassName) {
            return false;
        }
    }

    public static File uuidDir(final File root, final UUID uuid) {
        return new File(root, uuid.toString());
    }

    public static File viewDir(final File root, final UUID uuid,
                               final String digest, final boolean mkdirs) throws IOException {
        final File uuidDir = uuidDir(root, uuid);
        final File viewDir = new File(uuidDir, digest);
        if (mkdirs) {
            viewDir.mkdirs();
        }
        return viewDir;
    }

    private static long now() {
        return System.nanoTime();
    }

    private final HttpClient client;

    private boolean closed;

    private Context context;

    private final Database database;

    private UpdateSequence ddoc_seq;

    private long lastCommit;

    private final CountDownLatch latch = new CountDownLatch(1);

    private final Logger logger;

    private final Map<String, View> paths = new HashMap<String, View>();

    private HttpUriRequest req;

    private final File root;

    private UpdateSequence since;

    private final Map<View, IndexState> states = Collections
            .synchronizedMap(new HashMap<View, IndexState>());

    private UUID uuid;

    private final HierarchicalINIConfiguration ini;

    public DatabaseIndexer(final HttpClient client, final File root,
                           final Database database, final HierarchicalINIConfiguration ini)
            throws IOException, JSONException {
        this.client = client;
        this.root = root;
        this.database = database;
        this.ini = ini;
        this.logger = Logger.getLogger(DatabaseIndexer.class.getName() + "."
                + database.getInfo().getName());
    }

    public void admin(final HttpServletRequest req,
                      final HttpServletResponse resp) throws IOException, JSONException {
        final IndexState state = getState(req, resp);
        if (state == null)
            return;
        final String command = new PathParts(req).getCommand();

        if ("_expunge".equals(command)) {
            logger.info("Expunging deletes from " + state);
            state.writer.forceMergeDeletes(false);
            resp.setStatus(202);
            ServletUtils.sendJsonSuccess(req, resp);
            return;
        }

        if ("_optimize".equals(command)) {
            logger.info("Optimizing " + state);
            state.writer.forceMerge(1, false);
            resp.setStatus(202);
            ServletUtils.sendJsonSuccess(req, resp);
            return;
        }

        ServletUtils.sendJsonError(req, resp, 400, "bad_request");
    }

    public void awaitInitialization() {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            // Ignore.
        }
    }

    public Void handleResponse(final HttpResponse response)
            throws ClientProtocolException, IOException {
        final HttpEntity entity = response.getEntity();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(
                entity.getContent(), "UTF-8"));
        String line;
        loop:
        while ((line = reader.readLine()) != null) {
            maybeCommit();

            // Heartbeat.
            if (line.length() == 0) {
                logger.trace("heartbeat");
                continue loop;
            }

            try {
                final JSONObject json = new JSONObject(line);

                if (json.has("error")) {
                    logger.warn("Indexing stopping due to error: " + json);
                    break loop;
                }

                if (json.has("last_seq")) {
                    logger.info("End of changes detected.");
                    break loop;
                }

                final UpdateSequence seq = UpdateSequence.parseUpdateSequence(json.getString("seq"));
                final String id = json.getString("id");
                CouchDocument doc;
                if (!json.isNull("doc")) {
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
                    if (seq.isLaterThan(ddoc_seq)) {
                        logger.info("Exiting due to design document change.");
                        break loop;
                    }
                }

                if (doc.isDeleted()) {
                    for (final IndexState state : states.values()) {
                        state.writer.deleteDocuments(new Term("_id", id));
                        state.setPendingSequence(seq);
                        state.readerDirty = true;
                    }
                } else {
                    for (final Entry<View, IndexState> entry : states.entrySet()) {
                        final View view = entry.getKey();
                        final IndexState state = entry.getValue();

                        if (seq.isLaterThan(state.pending_seq)) {
                            final Collection<Document> docs;
                            try {
                                docs = state.converter.convert(doc, view
                                        .getDefaultSettings(), database);
                            } catch (final Exception e) {
                                logger.warn(id + " caused " + e.getMessage());
                                continue loop;
                            }

                            state.writer.updateDocuments(new Term("_id", id), docs,
                                    view.getAnalyzer());
                            state.setPendingSequence(seq);
                            state.readerDirty = true;
                        }
                    }
                }
            } catch (final JSONException e) {
                logger.error("JSON exception in changes loop", e);
                break loop;
            }
        }
        req.abort();
        return null;
    }

    public void info(final HttpServletRequest req,
                     final HttpServletResponse resp) throws IOException, JSONException {
        final IndexState state = getState(req, resp);
        if (state == null)
            return;
        final DirectoryReader reader = state.borrowReader(true);
        try {
            final JSONObject result = new JSONObject();
            result.put("current", reader.isCurrent());
            result.put("disk_size", Utils.directorySize(reader.directory()));
            result.put("doc_count", reader.numDocs());
            result.put("doc_del_count", reader.numDeletedDocs());
            result.put("uuid", state.getUuid());
            result.put("digest", state.getDigest());
            result.put("update_seq", getUpdateSequence(reader.getIndexCommit().getUserData()));
            final JSONArray fields = new JSONArray();
            for (AtomicReaderContext leaf : reader.leaves()) {
                for (FieldInfo info : leaf.reader().getFieldInfos()) {
                    if (info.name.startsWith("_")) {
                        continue;
                    }
                    if (info.isIndexed()) {
                        fields.put(info.name);
                    }
                }
            }
            result.put("fields", fields);
            result.put("version", reader.getVersion());
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

    public void run() {
        if (closed) {
            throw new IllegalStateException("closed!");
        }

        try {
            init();
        } catch (final Exception e) {
            logger.warn("Exiting after init() raised exception.", e);
            close();
            return;
        }

        try {
            try {
                final long changes_timeout = ini.getLong("lucene.changes_timeout", -1);
                req = database.getChangesRequest(since, changes_timeout);
                logger.info("Indexing from update_seq " + since);
                client.execute(req, this);
            } finally {
                close();
            }
        } catch (final SocketException e) {
            // Ignored because req.abort() does this.
        } catch (final Exception e) {
            logger.warn("Exiting due to exception.", e);
            close();
        }
    }

    public void search(final HttpServletRequest req,
                       final HttpServletResponse resp) throws IOException, JSONException {
        final IndexState state = getState(req, resp);
        if (state == null)
            return;
        final IndexSearcher searcher = state.borrowSearcher(isStaleOk(req));
        final String etag = state.getEtag();
        final FastVectorHighlighter fvh = new FastVectorHighlighter(true, true);
        final JSONArray result = new JSONArray();
        try {
            if (state.notModified(req)) {
                resp.setStatus(304);
                return;
            }
            for (final String queryString : getQueryStrings(req)) {
                final Analyzer analyzer = state.analyzer(req.getParameter("analyzer"));
                final Operator operator = "and".equalsIgnoreCase(req.getParameter("default_operator"))
                        ? Operator.AND : Operator.OR;
                final Query q = state.parse(queryString, operator, analyzer);

                final JSONObject queryRow = new JSONObject();
                queryRow.put("q", q.toString());
                if (getBooleanParameter(req, "debug")) {
                    queryRow.put("plan", QueryPlan.toPlan(q));
                    queryRow.put("analyzer", analyzer.getClass());
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
                        final int freq = searcher.getIndexReader().docFreq((Term) term);
                        freqs.put(term.toString(), freq);
                    }
                    queryRow.put("freqs", freqs);
                } else {
                    // Perform the search.
                    final TopDocs td;
                    final StopWatch stopWatch = new StopWatch();

                    final boolean include_docs = getBooleanParameter(req,
                            "include_docs");
                    final int highlights = getIntParameter(req, "highlights", 0);
                    final int highlight_length = max(getIntParameter(req, "highlight_length", 18), 18); // min for fast term vector highlighter is 18
                    final boolean include_termvectors = getBooleanParameter(req, "include_termvectors");
                    final int limit = getIntParameter(req, "limit",
                            ini.getInt("lucene.limit", 25));
                    final Sort sort = CustomQueryParser.toSort(req
                            .getParameter("sort"));
                    final int skip = getIntParameter(req, "skip", 0);

                    final Set<String> fieldsToLoad;
                    if (req.getParameter("include_fields") == null) {
                        fieldsToLoad = null;
                    } else {
                        final String[] fields = Utils.splitOnCommas(
                                req.getParameter("include_fields"));
                        final List<String> list = Arrays.asList(fields);
                        fieldsToLoad = new HashSet<String>(list);
                    }

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
                        final Document doc;
                        if (fieldsToLoad == null) {
                            doc = searcher.doc(td.scoreDocs[i].doc);
                        } else {
                            doc = searcher.doc(td.scoreDocs[i].doc, fieldsToLoad);
                        }

                        final JSONObject row = new JSONObject();
                        final JSONObject fields = new JSONObject();
                        final JSONObject highlight_rows = new JSONObject();

                        // Include stored fields.
                        for (final IndexableField f : doc.getFields()) {
                            if (!f.fieldType().stored()) {
                                continue;
                            }
                            final String name = f.name();
                            final Object value;
                            if (f.numericValue() != null) {
                                value = f.numericValue();
                            } else {
                                value = f.stringValue();
                            }
                            if (value != null) {
                                if ("_id".equals(name)) {
                                    row.put("id", value);
                                } else {
                                    if (!fields.has(name)) {
                                        fields.put(name, value);
                                    } else {
                                        final Object obj = fields.get(name);
                                        if (obj instanceof String || obj instanceof Number) {
                                            final JSONArray arr = new JSONArray();
                                            arr.put(obj);
                                            arr.put(value);
                                            fields.put(name, arr);
                                        } else {
                                            assert obj instanceof JSONArray;
                                            ((JSONArray) obj).put(value);
                                        }
                                    }

                                    if (highlights > 0) {
                                        String[] frags = fvh.getBestFragments(fvh.getFieldQuery(q), searcher.getIndexReader(), td.scoreDocs[i].doc, name, highlight_length, highlights);
                                        highlight_rows.put(name, frags);
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
                        if (fields.length() > 0) {
                            row.put("fields", fields);
                        }
                        if (highlight_rows.length() > 0) {
                            row.put("highlights", highlight_rows);
                        }

                        rows.put(row);
                    }
                    // Fetch documents (if requested).
                    if (include_docs && fetch_ids.length > 0) {
                        final List<CouchDocument> fetched_docs = database
                                .getDocuments(fetch_ids);
                        for (int j = 0; j < max; j++) {
                            final CouchDocument doc = fetched_docs.get(j);
                            final JSONObject row = doc == null ?
                                    new JSONObject("{\"error\":\"not_found\"}") :
                                    doc.asJson();
                            rows.getJSONObject(j).put("doc", row);
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
                                .toJSON(((TopFieldDocs) td).fields));
                    }
                    queryRow.put("rows", rows);
                }
                result.put(queryRow);
            }
        } catch (final ParseException e) {
            ServletUtils.sendJsonError(req, resp, 400, "Bad query syntax: "
                    + e.getMessage());
            return;
        } finally {
            state.returnSearcher(searcher);
        }

        resp.setHeader("ETag", etag);
        resp.setHeader("Cache-Control", "must-revalidate");
        ServletUtils.setResponseContentTypeAndEncoding(req, resp);

        final Object json = result.length() > 1 ? result : result.getJSONObject(0);
        final String callback = req.getParameter("callback");
        final String body;
        if (callback != null) {
            body = String.format("%s(%s)", callback, json);
        } else {
            if (json instanceof JSONObject) {
                final JSONObject obj = (JSONObject) json;
                body = getBooleanParameter(req, "debug") ?
                        obj.toString(2) : obj.toString();
            } else {
                final JSONArray arr = (JSONArray) json;
                body = getBooleanParameter(req, "debug") ?
                        arr.toString(2) : arr.toString();
            }
        }

        final Writer writer = resp.getWriter();
        try {
            writer.write(body);
        } finally {
            writer.close();
        }
    }

    private String[] getQueryStrings(final HttpServletRequest req) {
        return Utils.splitOnCommas(req.getParameter("q"));
    }

    private void close() {
        this.closed = true;

        for (final IndexState state : states.values()) {
            try {
                state.close();
            } catch (final IOException e) {
                logger.warn("Error while closing.", e);
            }
        }
        states.clear();
        if (context != null) {
            Context.exit();
            context = null;
        }
        latch.countDown();
    }

    public boolean isClosed() {
        return closed;
    }

    private void commitAll() throws IOException {
        for (final Entry<View, IndexState> entry : states.entrySet()) {
            final View view = entry.getKey();
            final IndexState state = entry.getValue();

            if (state.pending_seq.isLaterThan(getUpdateSequence(state.writer))) {
                final Map<String, String> userData = new HashMap<String, String>();
                userData.put("last_seq", state.pending_seq.toString());
                state.writer.setCommitData(userData);
                state.writer.commit();
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

    private IndexState getState(final HttpServletRequest req,
                                final HttpServletResponse resp) throws IOException, JSONException {
        final View view = paths.get(toPath(req));
        if (view == null) {
            ServletUtils.sendJsonError(req, resp, 400, "no_such_view");
            return null;
        }

        final IndexState result = states.get(view);
        if (result == null) {
            ServletUtils.sendJsonError(req, resp, 400, "no_such_state");
        }
        return result;
    }

    private UpdateSequence getUpdateSequence(final Directory dir) throws IOException {
        if (!DirectoryReader.indexExists(dir)) {
            return UpdateSequence.START;
        }
        final List<IndexCommit> commits = DirectoryReader.listCommits(dir);
        final IndexCommit latest = commits.get(commits.size() - 1);
        return getUpdateSequence(latest.getUserData());
    }

    private UpdateSequence getUpdateSequence(final IndexWriter writer) throws IOException {
        return getUpdateSequence(writer.getDirectory());
    }

    private UpdateSequence getUpdateSequence(final Map<String, String> userData) {
        if (userData != null && userData.containsKey("last_seq")) {
            return UpdateSequence.parseUpdateSequence(userData.get("last_seq"));
        }
        return UpdateSequence.START;
    }

    private void init() throws IOException, JSONException {
        this.uuid = database.getOrCreateUuid();

        this.context = Context.enter();
        context.setClassShutter(new RestrictiveClassShutter());
        context.setOptimizationLevel(9);

        this.ddoc_seq = database.getInfo().getUpdateSequence();
        this.since = null;

        for (final DesignDocument ddoc : database.getAllDesignDocuments()) {
            for (final Entry<String, View> entry : ddoc.getAllViews()
                    .entrySet()) {
                final String name = entry.getKey();
                final View view = entry.getValue();
                paths.put(toPath(ddoc.getId(), name), view);

                if (!states.containsKey(view)) {
                    final Directory dir = FSDirectory.open(viewDir(view, true),
                            new SingleInstanceLockFactory());
                    final UpdateSequence seq = getUpdateSequence(dir);
                    if (since == null) {
                        since = seq;
                    }
                    since = seq.isEarlierThan(since) ? seq : since;
                    logger.debug(dir + " bumped since to " + since);

                    final DocumentConverter converter = new DocumentConverter(
                            context, view);
                    final IndexWriter writer = newWriter(dir);

                    final IndexState state = new IndexState(converter, writer,
                            view.getAnalyzer(), database, view);
                    state.setPendingSequence(seq);
                    states.put(view, state);
                }
            }
        }
        if (since == null) {
            since = UpdateSequence.START;
        }
        logger.debug("paths: " + paths);

        this.lastCommit = now();
        latch.countDown();
    }

    private boolean isStaleOk(final HttpServletRequest req) {
        return "ok".equals(req.getParameter("stale"));
    }

    private void maybeCommit() throws IOException {
        if (now() - lastCommit >= getCommitInterval()) {
            commitAll();
        }
    }

    private IndexWriter newWriter(final Directory dir) throws IOException {
        final IndexWriterConfig config = new IndexWriterConfig(
                Constants.VERSION, Constants.ANALYZER);
        config.setUseCompoundFile(ini.getBoolean("lucene.useCompoundFile",
                false));
        config.setRAMBufferSizeMB(ini.getDouble("lucene.ramBufferSizeMB",
                IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB));

        return new IndexWriter(dir, config);
    }

    private File viewDir(final View view, final boolean mkdirs)
            throws IOException {
        return viewDir(root, uuid, view.getDigest(), mkdirs);
    }

    private long getSearchTimeout() {
        return ini.getLong("lucene.timeout", 5000);
    }

    private long getCommitInterval() {
        final long commitSeconds = max(1L, ini
                .getLong("lucene.commitEvery", 15));
        return SECONDS.toNanos(commitSeconds);
    }

    private String toPath(final HttpServletRequest req) {
        final PathParts parts = new PathParts(req);
        return toPath(parts.getDesignDocumentName(), parts.getViewName());
    }

    private String toPath(final String ddoc, final String view) {
        return ddoc + "/" + view;
    }

}
