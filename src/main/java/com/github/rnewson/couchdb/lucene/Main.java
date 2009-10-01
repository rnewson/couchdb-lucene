package com.github.rnewson.couchdb.lucene;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
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
        final boolean realtime = Boolean.parseBoolean(properties.getProperty("lucene.realtime", "false"));

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
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUserAgent(params, HttpProtocolParams.getUserAgent(params) + " couchdb-lucene/0.5");
        final SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 5984));
        final ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
        final HttpClient httpClient = new DefaultHttpClient(cm, params);

        // Configure other objects.
        final Couch couch = new Couch(httpClient, couchUrl);
        final Locator locator = new Locator();
        final LuceneGateway gateway = new LuceneGateway(new File(luceneDir), realtime);
        final State state = new State(couch, gateway, locator, httpClient);

        // Configure Indexer.
        final Indexer indexer = new Indexer(state);

        // Configure Jetty.
        final Server server = new Server(Integer.getInteger("port", lucenePort));
        server.setStopAtShutdown(true);
        server.setSendServerVersion(false);
        server.addLifeCycle(indexer);

        // TODO deuglify this.
        RhinoDocument.state = state;

        final ContextHandlerCollection contexts = new ContextHandlerCollection();
        server.setHandler(contexts);

        final Context search = new Context(contexts, "/search", Context.NO_SESSIONS);
        search.addServlet(new ServletHolder(new SearchServlet(state)), "/*");
        setupContext(search);

        final Context info = new Context(contexts, "/info", Context.NO_SESSIONS);
        info.addServlet(new ServletHolder(new InfoServlet(state)), "/*");
        setupContext(info);

        final Context admin = new Context(contexts, "/admin", Context.NO_SESSIONS);
        admin.addServlet(new ServletHolder(new AdminServlet(state)), "/*");
        setupContext(admin);

        // Lockdown
        // System.setSecurityManager(securityManager);

        server.start();
        server.join();
    }

    private static void setupContext(final Context context) {
        context.addFilter(new FilterHolder(new GzipFilter()), "/*", Handler.DEFAULT);
    }

}
