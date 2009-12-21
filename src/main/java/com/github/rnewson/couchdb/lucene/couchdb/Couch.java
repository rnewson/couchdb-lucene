package com.github.rnewson.couchdb.lucene.couchdb;

import java.io.IOException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.http.client.HttpClient;

/**
 * Simple Java API access to CouchDB.
 * 
 * @author robertnewson
 * 
 */
public class Couch {

    private final HttpClient httpClient;
    private final String url;

    public static Couch getInstance(final HttpClient httpClient, final String url) throws IOException {
        final Couch result = new Couch(httpClient, url);
        final JSONObject info = result.getInfo();
        if (info.getString("version").compareTo("0.10") < 0) {
            throw new IllegalStateException("Incompatible version found: " + info);
        }
        return result;
    }

    private Couch(final HttpClient httpClient, final String url) throws IOException {
        this.httpClient = httpClient;
        this.url = url.endsWith("/") ? url : url + "/";
    }

    public final String[] getAllDatabases() throws IOException {
        final String response = HttpUtils.get(httpClient, url + "_all_dbs");
        final JSONArray arr = JSONArray.fromObject(response);
        return (String[]) arr.toArray(new String[0]);
    }

    public final JSONObject getInfo() throws IOException {
        return JSONObject.fromObject(HttpUtils.get(httpClient, url));
    }

    public Database getDatabase(final String dbname) throws IOException {
        return new Database(httpClient, url + dbname);
    }

}
