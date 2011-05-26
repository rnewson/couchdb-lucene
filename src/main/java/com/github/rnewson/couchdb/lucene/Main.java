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

import java.io.File;

import org.apache.log4j.Logger;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
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
        final Config config = new Config();
        final File dir = config.getDir();

        final Server server = new Server();
        final SelectChannelConnector connector = new SelectChannelConnector();
        connector.setHost(config.getConfiguration().getString("lucene.host", "localhost"));
        connector.setPort(config.getConfiguration().getInt("lucene.port", 5985));

        LOG.info("Accepting connections with " + connector);

        server.setConnectors(new Connector[]{connector});
        server.setStopAtShutdown(true);
        server.setSendServerVersion(false);

        final LuceneServlet servlet = new LuceneServlet(config.getClient(), dir, config.getConfiguration());

        final Context context = new Context(server, "/", Context.NO_SESSIONS | Context.NO_SECURITY);
        context.addServlet(new ServletHolder(servlet), "/*");
        context.addFilter(new FilterHolder(new GzipFilter()), "/*", Handler.DEFAULT);
        context.setErrorHandler(new JSONErrorHandler());
        server.setHandler(context);

        server.start();
        server.join();
    }

}
