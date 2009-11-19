package com.github.rnewson.couchdb.lucene.couchdb;

import java.io.IOException;

import net.sf.json.JSONArray;

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

    private static class CouchWithoutChanges extends Couch {

        private CouchWithoutChanges(final HttpClient client, final String url) {
            super(client, url);
        }

        @Override
        public Database getDatabase(final String dbname) {
            return new Database.DatabaseWithoutChanges(httpClient, url + Utils.urlEncode(dbname));
        }
        
        @Override
        public String toString() {
            return "CouchDB without _changes";
        }
        
    }

    private static class CouchWithChanges extends Couch {

        private CouchWithChanges(final HttpClient client, final String url) {
            super(client, url);
        }

        @Override
        public Database getDatabase(final String dbname) {
            return new Database.DatabaseWithChanges(httpClient, url + Utils.urlEncode(dbname));
        }

        @Override
        public String toString() {
            return "CouchDB with _changes";
        }
        
    }

    private static final Logger LOG = Logger.getLogger(Couch.class);

    public static Couch getInstance(final HttpClient client, final String url) throws IOException {
        final String version = getCouchVersion(client, url);

        if (version.contains("CouchDB/0.11")) {
            return new CouchWithChanges(client, url);
        }
        if (version.contains("CouchDB/0.10")) {
            return new CouchWithoutChanges(client, url);
        }
        if (version.contains("CouchDB/0.9.1")) {
            return new CouchWithoutChanges(client, url);
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
