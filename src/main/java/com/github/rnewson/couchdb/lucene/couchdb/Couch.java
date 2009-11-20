package com.github.rnewson.couchdb.lucene.couchdb;

import java.io.IOException;

import net.sf.json.JSONArray;

import org.apache.http.client.HttpClient;

/**
 * Simple Java API access to CouchDB.
 * 
 * @author robertnewson
 * 
 */
public final class Couch {

    private final HttpClient httpClient;
    private final String url;

    public Couch(final HttpClient httpClient, final String url) {
        this.httpClient = httpClient;
        this.url = url.endsWith("/") ? url : url + "/";
    }

    public final String[] getAllDatabases() throws IOException {
        final String response = HttpUtils.get(httpClient, url + "_all_dbs");
        final JSONArray arr = JSONArray.fromObject(response);
        return (String[]) arr.toArray(new String[0]);
    }

    public Database getDatabase(final String dbname) {
        return new Database(httpClient, url + dbname);
    }

}
