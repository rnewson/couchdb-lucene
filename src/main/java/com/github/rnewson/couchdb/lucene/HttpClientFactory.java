package com.github.rnewson.couchdb.lucene;

import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

/**
 * HttpClient instances just the way we like them.
 * 
 * @author robertnewson
 *
 */
public final class HttpClientFactory {

    public static HttpClient getInstance() {
        final HttpParams params = new BasicHttpParams();
        // protocol params.
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUseExpectContinue(params, false);
        // connection params.
        HttpConnectionParams.setTcpNoDelay(params, true);
        HttpConnectionParams.setStaleCheckingEnabled(params, false);

        return new DefaultHttpClient(params);
    }

}
