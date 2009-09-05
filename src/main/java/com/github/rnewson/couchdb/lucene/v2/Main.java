package com.github.rnewson.couchdb.lucene.v2;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

import javax.servlet.Filter;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
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
        properties.load(in);
        in.close();
        
        final String luceneDir = properties.getProperty("lucene.dir");
        final int lucenePort = Integer.parseInt(properties.getProperty("lucene.port", "5985"));
        final String couchUrl = properties.getProperty("couchdb.url");
        
        if (luceneDir == null) {
            LOG.error("lucene.dir not set.");
            System.exit(1);            
        }

        if (couchUrl== null) {
            LOG.error("couchdb.url not set.");
            System.exit(1);            
        }

        final HttpClient httpClient = new DefaultHttpClient();
        final Database database = new Database(httpClient, couchUrl);            

        final LuceneHolders holders = new LuceneHolders(new File(luceneDir), false);
        
        // Configure Indexer.
        final Indexer indexer = new Indexer(database, holders);
                
        // Configure Jetty.
        final Server server = new Server(Integer.getInteger("port", lucenePort));
        server.setStopAtShutdown(true);
        server.setSendServerVersion(false);
        server.addLifeCycle(indexer);

        final Filter gzipFilter = new GzipFilter();
                
        final ContextHandlerCollection contexts = new ContextHandlerCollection();
        server.setHandler(contexts);

        final Context search = new Context(contexts, "/search", Context.NO_SESSIONS);
        search.addFilter(new FilterHolder(gzipFilter), "/*", Handler.DEFAULT);
        search.addServlet(new ServletHolder(new SearchServlet(holders, database)), "/*");

        final Context info = new Context(contexts, "/info", Context.NO_SESSIONS);
        info.addServlet(new ServletHolder(new InfoServlet(holders)), "/*");

        final Context admin = new Context(contexts, "/admin", Context.NO_SESSIONS);
        admin.addServlet(new ServletHolder(new AdminServlet(holders)), "/*");
        
        server.start();
        server.join();
    }

}
