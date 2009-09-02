package com.github.rnewson.couchdb.lucene;

import java.io.IOException;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;

import com.github.rnewson.couchdb.lucene.util.StatusCodeResponseHandler;

public final class Config {

    private static final String PREFIX = "lucene/";

    private HttpClient httpClient;

    private String url;

    public void setHttpClient(final HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getStringProperty(final String name) throws IOException {
        return get(name);
    }

    public int setStringProperty(final String name, final String value) throws IOException {
        return put(name, value);
    }

    public int getIntProperty(final String name) throws IOException {
        return Integer.parseInt(get(name));
    }

    public int setIntProperty(final String name, final int value) throws IOException {
        return put(name, Integer.toString(value));
    }

    private String get(final String name) throws IOException {
        final HttpGet get = new HttpGet(url + PREFIX + name);
        return httpClient.execute(get, new BasicResponseHandler());
    }

    private int put(final String name, final String value) throws IOException {
        final HttpPut put = new HttpPut(url + PREFIX + name);
        put.setEntity(new StringEntity(value));
        return httpClient.execute(put, new StatusCodeResponseHandler());
    }

}
