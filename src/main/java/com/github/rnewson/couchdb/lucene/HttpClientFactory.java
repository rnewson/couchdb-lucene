package com.github.rnewson.couchdb.lucene;

/**
 * Copyright 2009 Robert Newson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.concurrent.TimeUnit;

import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

/**
 * HttpClient instances just the way we like them.
 * 
 * @author rnewson
 * 
 */
public final class HttpClientFactory {

    private static class ShieldedClientConnManager implements ClientConnectionManager {

        private final ClientConnectionManager delegate;

        public ShieldedClientConnManager(final ClientConnectionManager delegate) {
            this.delegate = delegate;
        }

        public void closeExpiredConnections() {
            delegate.closeExpiredConnections();
        }

        public void closeIdleConnections(final long idletime, final TimeUnit tunit) {
            delegate.closeIdleConnections(idletime, tunit);
        }

        public SchemeRegistry getSchemeRegistry() {
            return delegate.getSchemeRegistry();
        }

        public void releaseConnection(final ManagedClientConnection conn, final long validDuration, final TimeUnit timeUnit) {
            delegate.releaseConnection(conn, validDuration, timeUnit);
        }

        public ClientConnectionRequest requestConnection(final HttpRoute route, final Object state) {
            return delegate.requestConnection(route, state);
        }

        public void shutdown() {
            // SHIELDED.
            // delegate.shutdown();
        }

    }

    private static HttpClient instance;

    public static synchronized HttpClient getInstance() {
        if (instance == null) {
            final HttpParams params = new BasicHttpParams();
            // protocol params.
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setUseExpectContinue(params, false);
            // connection params.
            HttpConnectionParams.setTcpNoDelay(params, true);
            HttpConnectionParams.setStaleCheckingEnabled(params, false);
            ConnManagerParams.setMaxTotalConnections(params, 1000);
            ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(1000));

            final SchemeRegistry schemeRegistry = new SchemeRegistry();
            schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 5984));
            final ClientConnectionManager cm = new ShieldedClientConnManager(
                    new ThreadSafeClientConnManager(params, schemeRegistry));

            instance = new DefaultHttpClient(cm, params);
        }
        return instance;
    }

}
