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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import jakarta.servlet.DispatcherType;
import java.io.File;
import java.util.EnumSet;

public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private Main() {
        throw new InstantiationError("This class is not supposed to be instantiated.");
    }
    /**
     * Run couchdb-lucene.
     */
    public static void main(String[] args) throws Exception {
        final Config config = new Config();
        final File dir = config.getDir();

        final Server server = new Server();

        final ServerConnector connector = new ServerConnector(server);
        connector.setHost(config.getConfiguration().getString("lucene.host", "localhost"));
        connector.setPort(config.getConfiguration().getInt("lucene.port", 5985));

        LOG.info("Accepting connections with " + connector);

        server.addConnector(connector);
        server.setStopAtShutdown(true);

        final LuceneServlet servlet = new LuceneServlet(config.getClient(), dir, config.getConfiguration());

        final ServletContextHandler context = new ServletContextHandler(server, "/",
                ServletContextHandler.NO_SESSIONS | ServletContextHandler.NO_SECURITY);
        context.addServlet(new ServletHolder(servlet), "/*");
        context.setErrorHandler(new JSONErrorHandler());
        context.setGzipHandler(new GzipHandler());
        server.setHandler(context);

        server.start();
        server.join();
    }

}
