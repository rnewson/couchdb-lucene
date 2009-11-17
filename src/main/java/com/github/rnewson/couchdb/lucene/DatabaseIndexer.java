package com.github.rnewson.couchdb.lucene;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

import net.sf.json.JSONObject;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.log4j.Logger;
import org.apache.tika.io.IOUtils;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;

/**
 * Tracks changes to a particular database on a particular host, applying all
 * changes to all indexes.
 * 
 * @author robertnewson
 * 
 */
public final class DatabaseIndexer implements Runnable {

    private final class RestrictiveClassShutter implements ClassShutter {
        public boolean visibleToScripts(final String fullClassName) {
            return false;
        }
    }

    private static HttpClient httpClient() {
        final HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUseExpectContinue(params, false);
        return new DefaultHttpClient(params);
    }

    private static class UUIDHandler implements ResponseHandler<UUID> {

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

    private final String url;
    private HttpClient client;
    private Context context;
    private UUID uuid;

    private final Logger logger;

    public DatabaseIndexer(final String url) {
        this.url = url;
        logger = Logger.getLogger(url);
    }

    public void run() {
        setup();
        try {
            while (true) {
                try {
                    index();
                } catch (final IOException e) {
                    logger.info("I/O exception while indexing.");
                    try {
                        SECONDS.sleep(5);
                    } catch (final InterruptedException e1) {
                        logger.debug("Interrupted while sleeping.");
                        return;
                    }
                }
            }
        } finally {
            teardown();
        }
    }

    private void setup() {
        client = httpClient();
        context = Context.enter();
        context.setClassShutter(new RestrictiveClassShutter());
        context.setOptimizationLevel(9);
    }

    private void index() throws IOException {
        HttpGet get = new HttpGet(url + "/_local/lucene");
        uuid = getDatabaseUuid(get);
        if (uuid == null) {
            storeNewUuid();
        }
    }

    private UUID getDatabaseUuid(HttpGet get) throws IOException, ClientProtocolException {
        return client.execute(get, new UUIDHandler());
    }

    private void storeNewUuid() throws UnsupportedEncodingException, IOException, ClientProtocolException {
        final JSONObject json = new JSONObject();
        final UUID newUUID = UUID.randomUUID();
        json.put("uuid", newUUID.toString());
        final HttpPut put = new HttpPut(url + "/_local/lucene");
        put.setEntity(new StringEntity(json.toString()));
        client.execute(put, new BasicResponseHandler());
    }

    private void teardown() {
        try {
            Context.exit();
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
