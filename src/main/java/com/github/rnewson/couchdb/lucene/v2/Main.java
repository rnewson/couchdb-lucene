package com.github.rnewson.couchdb.lucene.v2;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

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

        final Directory dir = FSDirectory.open(new File(luceneDir));
        final HttpClient httpClient = new DefaultHttpClient();
        final Database database = new Database(httpClient, couchUrl);            

        final LuceneHolder holder = new LuceneHolder(dir, false);
        
        final Server server = new Server(Integer.getInteger("port", 5985));
        final ContextHandlerCollection contexts = new ContextHandlerCollection();
        server.setHandler(contexts);

        final Context index = new Context(contexts, "/index", Context.NO_SESSIONS);
        index.addServlet(new ServletHolder(new IndexingServlet(holder)), "/");

        final Context search = new Context(contexts, "/search", Context.NO_SESSIONS);
        search.addServlet(new ServletHolder(new SearchServlet(holder, database)), "/*");

        final Context info = new Context(contexts, "/info", Context.NO_SESSIONS);
        info.addServlet(new ServletHolder(new InfoServlet(holder)), "/*");
        
        server.start();
        server.join();
    }

}
