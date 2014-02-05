package com.github.rnewson.couchdb.lucene;

import java.io.IOException;
import java.util.Collection;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

public class AuthenticatingHttpClient implements HttpClient {
    private final HttpClient delegate;
    private final Collection<Header> headers;

    public AuthenticatingHttpClient(HttpClient delegate, Collection<Header> headers) {
        this.delegate = delegate;
        this.headers = headers;
    }

    public HttpParams getParams() {
        return delegate.getParams();
    }

    public ClientConnectionManager getConnectionManager() {
        return delegate.getConnectionManager();
    }

    public HttpResponse execute(HttpUriRequest request) throws IOException, ClientProtocolException {
        return delegate.execute(addHeaders(request));
    }

    public HttpResponse execute(HttpUriRequest request, HttpContext context) throws IOException,
            ClientProtocolException {
        return delegate.execute(addHeaders(request), context);
    }

    public HttpResponse execute(HttpHost target, HttpRequest request) throws IOException, ClientProtocolException {
        return delegate.execute(target, addHeaders(request));
    }

    public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context) throws IOException,
            ClientProtocolException {
        return delegate.execute(target, addHeaders(request), context);
    }

    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler) throws IOException,
            ClientProtocolException {
        return delegate.execute(addHeaders(request), responseHandler);
    }

    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException, ClientProtocolException {
        return delegate.execute(addHeaders(request), responseHandler, context);
    }

    public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> responseHandler)
            throws IOException, ClientProtocolException {
        return delegate.execute(target, addHeaders(request), responseHandler);
    }

    public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> responseHandler,
            HttpContext context) throws IOException, ClientProtocolException {
        return delegate.execute(target, addHeaders(request), responseHandler, context);
    }

    public <T extends HttpRequest> T addHeaders(T request) {
        for (Header h: headers) {
            request.addHeader(h);
        }
        return request;
    }
}
