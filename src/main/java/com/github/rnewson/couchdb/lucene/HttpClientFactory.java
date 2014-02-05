/*
 * Copyright Robert Newson
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

package com.github.rnewson.couchdb.lucene;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

/**
 * HttpClient instances just the way we like them.
 * 
 * @author rnewson
 * 
 */
public final class HttpClientFactory {

    private static final class PreemptiveAuthenticationRequestInterceptor
            implements HttpRequestInterceptor {

        public void process(final HttpRequest request, final HttpContext context)
                throws HttpException, IOException {

            final AuthState authState = (AuthState) context
                    .getAttribute(ClientContext.TARGET_AUTH_STATE);
            final CredentialsProvider credsProvider = (CredentialsProvider) context
                    .getAttribute(ClientContext.CREDS_PROVIDER);
            final HttpHost targetHost = (HttpHost) context
                    .getAttribute(ExecutionContext.HTTP_TARGET_HOST);

            // If not auth scheme has been initialized yet
            if (authState.getAuthScheme() == null) {
                AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
                // Obtain credentials matching the target host
                Credentials creds = credsProvider.getCredentials(authScope);
                // If found, generate BasicScheme preemptively
                if (creds != null) {
                    authState.setAuthScheme(new BasicScheme());
                    authState.setCredentials(creds);
                }
            }
        }
    }

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

        public void releaseConnection(
                final ManagedClientConnection conn,
                final long validDuration,
                final TimeUnit timeUnit) {
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

    private static DefaultHttpClient instance;

    private static HierarchicalINIConfiguration INI;

    public static synchronized HttpClient getInstance() throws MalformedURLException {
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
            schemeRegistry
                    .register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 5984));
            schemeRegistry
            		.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
            final ClientConnectionManager cm = new ShieldedClientConnManager(
                    new ThreadSafeClientConnManager(params, schemeRegistry));

            instance = new DefaultHttpClient(cm, params);

            if (INI != null) {
                final CredentialsProvider credsProvider = new BasicCredentialsProvider();
                final Iterator<?> it = INI.getKeys();
                while (it.hasNext()) {
                    final String key = (String) it.next();
                    if (!key.startsWith("lucene.") && key.endsWith(".url")) {
                        final URL url = new URL(INI.getString(key));
                        if (url.getUserInfo() != null) {
                            credsProvider.setCredentials(
                                    new AuthScope(url.getHost(), url.getPort()),
                                    new UsernamePasswordCredentials(url.getUserInfo()));
                        }
                    }
                }
                instance.setCredentialsProvider(credsProvider);
                instance.addRequestInterceptor(new PreemptiveAuthenticationRequestInterceptor(), 0);
            }
        }
        return instance;
    }

    public static void setIni(final HierarchicalINIConfiguration ini) {
        instance = null;
        INI = ini;
    }

}
