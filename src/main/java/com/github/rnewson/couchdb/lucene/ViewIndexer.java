package com.github.rnewson.couchdb.lucene;

import java.io.IOException;
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
import org.apache.lucene.index.IndexReader;
import org.apache.tika.io.IOUtils;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;

import com.github.rnewson.couchdb.lucene.Lucene.ReaderCallback;
import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.couchdb.Database.ChangesHandler;
import com.github.rnewson.couchdb.lucene.util.Analyzers;
import com.github.rnewson.couchdb.lucene.util.Constants;
import com.github.rnewson.couchdb.lucene.util.StatusCodeResponseHandler;

public final class ViewIndexer implements Runnable {

    private final Logger logger;
    private HttpClient client;
    private Context context;
    private final IndexKey key;
    private final CouchDbRegistry registry;
    private Database database;
    private String url;
    private final Lucene lucene;
    private long since;

    public ViewIndexer(final Lucene lucene, final CouchDbRegistry registry, final IndexKey key) {
        this.lucene = lucene;
        this.logger = Logger.getLogger(key.toString());
        this.registry = registry;
        this.key = key;
    }

    public void run() {
        try {
            setup();
        } catch (final IOException e) {
            logger.warn("I/O exception starting indexing: " + e.getMessage());
        }
        try {
            index();
        } catch (final IOException e) {
            logger.warn("I/O exception while indexing: " + e.getMessage());
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
        final Couch couch = Couch.getInstance(client, registry.createUrlByHostKey(key.getHostKey(), ""));
        logger.info(couch + " detected.");
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
        final JSONObject fulltext = ddoc.getJSONObject("fulltext");
        final JSONObject view = fulltext.getJSONObject(key.getViewName());
        final JSONObject defaults = view.has("defaults") ? view.getJSONObject("defaults") : defaults();
        final Analyzer analyzer = Analyzers.getAnalyzer(view.optString("analyzer", "standard"));
        final String function = StringUtils.trim(view.getString("index"));

        lucene.createWriter(key, uuid, function);
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
        logger.info("Fetching changes since update_seq " + since);

        database.handleChanges(since, new ViewChangesHandler());
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

    private class ViewChangesHandler implements ChangesHandler {

        public void onChange(long seq, JSONObject doc) throws IOException {
            // TODO Auto-generated method stub
            System.err.println(seq);
        }

        public void onEndOfSequence(long seq) throws IOException {
            // TODO Auto-generated method stub

        }

        public void onError(JSONObject error) throws IOException {
            // TODO Auto-generated method stub

        }

        public void onHeartbeat() throws IOException {
            // TODO Auto-generated method stub

        }

    }

}
