package com.github.rnewson.couchdb.lucene.couchdb;

import java.io.IOException;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;

import com.github.rnewson.couchdb.lucene.util.Constants;
import com.github.rnewson.couchdb.lucene.util.StatusCodeResponseHandler;

public final class HttpUtils {

    public static final int delete(final HttpClient httpClient, final String url) throws IOException {
        return httpClient.execute(new HttpDelete(url), new StatusCodeResponseHandler());
    }

    public static final String execute(final HttpClient httpClient, final HttpUriRequest request) throws IOException {
        return httpClient.execute(request, new BasicResponseHandler());
    }

    public static final String get(final HttpClient httpClient, final String url) throws IOException {
        return execute(httpClient, new HttpGet(url));
    }

    public static final String post(final HttpClient httpClient, final String url, final String body) throws IOException {
        final HttpPost post = new HttpPost(url);
        post.setEntity(new StringEntity(body));
        return execute(httpClient, post);
    }

    public static final int put(final HttpClient httpClient, final String url, final String body) throws IOException {
        final HttpPut put = new HttpPut(url);
        if (body != null) {
            put.setHeader("Content-Type", Constants.CONTENT_TYPE);
            put.setEntity(new StringEntity(body));
        }
        return httpClient.execute(put, new StatusCodeResponseHandler());
    }

}
