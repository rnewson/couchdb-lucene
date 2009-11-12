package com.github.rnewson.couchdb.lucene;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

import javax.servlet.http.HttpServlet;

import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.log4j.Logger;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.FilterHolder;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.servlet.GzipFilter;

import com.github.rnewson.couchdb.lucene.couchdb.Couch;

/**
 * Configure and start embedded Jetty server.
 * 
 * @author rnewson
 * 
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class);

    public static void main(final String[] args) throws Exception {
        final Properties properties = new Properties();
        final InputStream in = Main.class.getClassLoader().getResourceAsStream("couchdb-lucene.properties");
        if (in == null) {
            System.out.println("No couchdb-lucene.properties file found.");
            return;
        }
        properties.load(in);
        in.close();

        final String luceneDir = properties.getProperty("lucene.dir");
        final int lucenePort = Integer.parseInt(properties.getProperty("lucene.port", "5985"));
        final String couchUrl = properties.getProperty("couchdb.url");

        if (luceneDir == null) {
            LOG.error("lucene.dir not set.");
            System.exit(1);
        }

        if (couchUrl == null) {
            LOG.error("couchdb.url not set.");
            System.exit(1);
        }

        // Configure httpClient.
        final HttpParams params = new BasicHttpParams();
        ConnManagerParams.setMaxTotalConnections(params, 1000);
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRoute() {
            public int getMaxForRoute(final HttpRoute route) {
                return 1000;
            }
        });
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUseExpectContinue(params, false);
        final SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 5984));
        final ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
        final HttpClient httpClient = new DefaultHttpClient(cm, params);

        // Configure other objects.
        final Couch couch = Couch.getInstance(httpClient, couchUrl);
        final Locator locator = new Locator();
        final LuceneGateway lucene = new LuceneGateway(new File(luceneDir));

        // Configure Indexer.
        final Indexer indexer = new Indexer();
        indexer.setCouch(couch);
        indexer.setLocator(locator);
        indexer.setLucene(lucene);

        // Configure Jetty.
        final Server server = new Server(Integer.getInteger("port", lucenePort));
        server.setStopAtShutdown(true);
        server.setSendServerVersion(false);
        server.addLifeCycle(indexer);

        final ContextHandlerCollection contexts = new ContextHandlerCollection();
        server.setHandler(contexts);

        final SearchServlet search = new SearchServlet();
        search.setCouch(couch);
        search.setLocator(locator);
        search.setLucene(lucene);
        setupContext(contexts, "/search", search);

        final InfoServlet info = new InfoServlet();
        info.setLocator(locator);
        info.setLucene(lucene);
        setupContext(contexts, "/info", info);

        final AdminServlet admin = new AdminServlet();
        admin.setLocator(locator);
        admin.setLucene(lucene);
        setupContext(contexts, "/admin", admin);

        // Lockdown
        // System.setSecurityManager(securityManager);

        server.start();
        server.join();
    }

    private static void setupContext(final ContextHandlerCollection contexts, final String root, final HttpServlet servlet) {
        final Context context = new Context(contexts, root, Context.NO_SESSIONS);
        context.addServlet(new ServletHolder(servlet), "/*");
        context.addFilter(new FilterHolder(new GzipFilter()), "/*", Handler.DEFAULT);
        context.setErrorHandler(new JSONErrorHandler());
    }

}
