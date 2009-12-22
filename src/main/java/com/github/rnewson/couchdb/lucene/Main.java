package com.github.rnewson.couchdb.lucene;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.servlet.http.HttpServlet;

import org.apache.log4j.Logger;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.FilterHolder;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.servlet.GzipFilter;

public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class);

    /**
     * Run couchdb-lucene.
     */
    public static void main(String[] args) throws Exception {
        final Properties properties = loadProperties();

        final File dir = new File(properties.getProperty("lucene.dir", "indexes"));
        if (dir == null) {
            LOG.error("lucene.dir not set.");
            System.exit(1);
        }
        if (!dir.exists() && !dir.mkdir()) {
            LOG.error("Could not create " + dir.getCanonicalPath());
            System.exit(1);
        }
        if (!dir.canRead()) {
            LOG.error(dir + " is not readable.");
            System.exit(1);
        }
        if (!dir.canWrite()) {
            LOG.error(dir + " is not writable.");
            System.exit(1);
        }
        LOG.info("Index output goes to: " + dir.getCanonicalPath());

        final Lucene lucene = new Lucene(dir);
        final String host = properties.getProperty("lucene.host", "localhost");
        final int port = Integer.parseInt(properties.getProperty("lucene.port", "5985"));
        final Server jetty = jetty(lucene, host, port);

        jetty.start();
        jetty.join();
    }

    private static Properties loadProperties() throws IOException {
        final Properties properties = new Properties();
        final InputStream in = Main.class.getClassLoader().getResourceAsStream("couchdb-lucene.properties");
        if (in == null) {
            LOG.error("No couchdb-lucene.properties file found.");
            return null;
        }
        properties.load(in);
        in.close();
        return properties;
    }

    /**
     * Configure Jetty.
     */
    private static Server jetty(final Lucene lucene, final String host, final int port) {
        final Server server = new Server();

        final SelectChannelConnector connector = new SelectChannelConnector();
        connector.setHost(host);
        connector.setPort(port);
        
        LOG.info("Accepting connections with " + connector);
        
        server.setConnectors(new Connector[] { connector });
        server.setStopAtShutdown(true);
        server.setSendServerVersion(false);

        final ContextHandlerCollection contexts = new ContextHandlerCollection();
        server.setHandler(contexts);

        final SearchServlet search = new SearchServlet();
        search.setLucene(lucene);
        setupContext(contexts, "/search", search);

        final InfoServlet info = new InfoServlet();
        info.setLucene(lucene);
        setupContext(contexts, "/info", info);

        final AdminServlet admin = new AdminServlet();
        admin.setLucene(lucene);
        setupContext(contexts, "/admin", admin);

        final TestServlet test = new TestServlet();
        test.setLucene(lucene);
        setupContext(contexts, "/test", test);

        return server;
    }

    private static void setupContext(final ContextHandlerCollection contexts, final String root, final HttpServlet servlet) {
        final Context context = new Context(contexts, root, Context.NO_SESSIONS);
        context.addServlet(new ServletHolder(servlet), "/*");
        context.addFilter(new FilterHolder(new GzipFilter()), "/*", Handler.DEFAULT);
        context.setErrorHandler(new JSONErrorHandler());
    }

}
