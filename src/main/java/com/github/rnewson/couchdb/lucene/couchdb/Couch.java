package com.github.rnewson.couchdb.lucene.couchdb;

import java.io.IOException;

import net.sf.json.JSONArray;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.log4j.Logger;

import com.github.rnewson.couchdb.lucene.util.Utils;

/**
 * Simple Java API access to CouchDB.
 * 
 * @author robertnewson
 * 
 */
public abstract class Couch {

    private static class CouchV10 extends Couch {

        private CouchV10(final HttpClient client, final String url) {
            super(client, url);
        }

        @Override
        public Database getDatabase(final String dbname) {
            return new Database.V10(httpClient, url + Utils.urlEncode(dbname));
        }
    }

    private static class CouchV11 extends Couch {

        private CouchV11(final HttpClient client, final String url) {
            super(client, url);
        }

        @Override
        public Database getDatabase(final String dbname) {
            return new Database.V11(httpClient, url + Utils.urlEncode(dbname));
        }
    }

    private static final Logger LOG = Logger.getLogger(Couch.class);

    public static Couch getInstance(final HttpClient client, final String url) throws IOException {
        final String version = getCouchVersion(client, url);

        if (version.contains("CouchDB/0.11")) {
            LOG.info("CouchDB 0.11 detected.");
            return new CouchV11(client, url);
        }
        if (version.contains("CouchDB/0.10")) {
            LOG.info("CouchDB 0.10 detected.");
            return new CouchV10(client, url);
        }
        if (version.contains("CouchDB/0.9.1")) {
            LOG.info("CouchDB 0.9.1 detected.");
            return new CouchV10(client, url);
        }
        throw new UnsupportedOperationException("No support for " + version);
    }

    private static String getCouchVersion(final HttpClient client, final String url) throws IOException, ClientProtocolException {
        final ResponseHandler<String> handler = new ResponseHandler<String>() {
            public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                return response.getFirstHeader("Server").getValue();
            }
        };
        return client.execute(new HttpGet(url), handler);
    }

    protected final HttpClient httpClient;
    protected final String url;

    private Couch(final HttpClient httpClient, final String url) {
        this.httpClient = httpClient;
        this.url = url.endsWith("/") ? url : url + "/";
    }

    public final String[] getAllDatabases() throws IOException {
        final String response = HttpUtils.get(httpClient, url + "_all_dbs");
        final JSONArray arr = JSONArray.fromObject(response);
        return (String[]) arr.toArray(new String[0]);
    }

    public abstract Database getDatabase(final String dbname);

}
