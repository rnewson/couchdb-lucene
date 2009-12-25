package com.github.rnewson.couchdb.lucene;

import java.io.File;

import javax.servlet.http.HttpServlet;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
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
        final HierarchicalINIConfiguration configuration = new HierarchicalINIConfiguration(Main.class.getClassLoader()
                .getResource("couchdb-lucene.properties"));
        configuration.setReloadingStrategy(new FileChangedReloadingStrategy());

        final File dir = new File(configuration.getString("lucene.dir", "indexes"));

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

        final Server server = new Server();
        final SelectChannelConnector connector = new SelectChannelConnector();
        connector.setHost(configuration.getString("lucene.host", "localhost"));
        connector.setPort(configuration.getInt("lucene.port", 5985));

        LOG.info("Accepting connections with " + connector);

        server.setConnectors(new Connector[] { connector });
        server.setStopAtShutdown(true);
        server.setSendServerVersion(false);

        final ContextHandlerCollection contexts = new ContextHandlerCollection();
        server.setHandler(contexts);

        final SearchServlet search = new SearchServlet();
        search.setLucene(lucene);
        search.setConfiguration(configuration);
        setupContext(contexts, "/search", search);

        final InfoServlet info = new InfoServlet();
        info.setLucene(lucene);
        info.setConfiguration(configuration);
        setupContext(contexts, "/info", info);

        final AdminServlet admin = new AdminServlet();
        admin.setLucene(lucene);
        admin.setConfiguration(configuration);
        setupContext(contexts, "/admin", admin);

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
