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

import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.couchdb.DesignDocument;
import com.github.rnewson.couchdb.lucene.couchdb.View;
import com.github.rnewson.couchdb.lucene.util.ServletUtils;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.message.BasicHeader;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

public final class LuceneServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(LuceneServlet.class);

    private static final long serialVersionUID = 1L;

    private final HttpClient client;

    private final Map<Database, DatabaseIndexer> indexers = new HashMap<Database, DatabaseIndexer>();

    private final HierarchicalINIConfiguration ini;

    private final File root;

    private final Map<Database, Thread> threads = new HashMap<Database, Thread>();

    public LuceneServlet() throws ConfigurationException, IOException {
        final Config config = new Config();
        this.client = config.getClient();
        this.root = config.getDir();
        this.ini = config.getConfiguration();
    }

    public LuceneServlet(final HttpClient client, final File root,
                         final HierarchicalINIConfiguration ini) {
        this.client = client;
        this.root = root;
        this.ini = ini;
    }

    private void cleanup(final HttpServletRequest req,
                         final HttpServletResponse resp) throws IOException, JSONException {
        final Couch couch = getCouch(req);
        final Set<String> dbKeep = new HashSet<String>();
        final JSONArray databases = couch.getAllDatabases();
        for (int i = 0; i < databases.length(); i++) {
            final Database db = couch.getDatabase(databases.getString(i));
            final UUID uuid = db.getUuid();
            if (uuid == null) {
                continue;
            }
            dbKeep.add(uuid.toString());

            final Set<String> viewKeep = new HashSet<String>();

            for (final DesignDocument ddoc : db.getAllDesignDocuments()) {
                for (final View view : ddoc.getAllViews().values()) {
                    viewKeep.add(view.getDigest());
                }
            }

            // Delete all indexes except the keepers.
            final File[] dirs = DatabaseIndexer.uuidDir(root, db.getUuid()).listFiles();
            if (dirs != null) {
                for (final File dir : dirs) {
                    if (!viewKeep.contains(dir.getName())) {
                        LOG.info("Cleaning old index at " + dir);
                        FileUtils.deleteDirectory(dir);
                    }
                }
            }
        }

        // Delete all directories except the keepers.
        for (final File dir : root.listFiles()) {
            if (!dbKeep.contains(dir.getName())) {
                LOG.info("Cleaning old index at " + dir);
                FileUtils.deleteDirectory(dir);
            }
        }

        resp.setStatus(202);
        ServletUtils.sendJsonSuccess(req, resp);
    }

    private enum AuthMethod { None, Cookie, Basic };
    private static Pattern basicAuthParser = Pattern.compile( "^Basic (.+)" );

    private Couch getCouch(final HttpServletRequest req) throws IOException {
        final String sectionName = new PathParts(req).getKey();
        final Configuration section = ini.getSection(sectionName);
        if (!section.containsKey("url")) {
            throw new FileNotFoundException(sectionName + " is missing or has no url parameter.");
        }
        
        AuthMethod authMethod = AuthMethod.None;
        Cookie cookies[] = req.getCookies();
        if( cookies != null ) {
            for( int i = 0; i < cookies.length; i++ ) {
                // Pass through AuthSession cookie adding domain and path
                // domain and path are necessary for HttpClient cookie 
                // selection process to work.
                if( cookies[i].getName().equals( "AuthSession" ) ) {
                    BasicCookieStore cs = new BasicCookieStore();
                    BasicClientCookie c = new BasicClientCookie( 
                        cookies[i].getName(),
                        cookies[i].getValue()
                    );
                    String domain = (new URL(section.getString("url"))).getHost();
                    c.setDomain("." + domain);
                    c.setPath("/");
                    cs.addCookie( c );
                    ((DefaultHttpClient)client).setCookieStore( cs );
                    authMethod = AuthMethod.Cookie;
                }
            }
        }

        if( authMethod == AuthMethod.None ) {
            String basicAuth = req.getHeader( "Authorization" );
            if( basicAuth != null ) {
                Matcher m = basicAuthParser.matcher( basicAuth );
                if( m.matches() ) {
                    Header authHdr = new BasicHeader(
                        HttpHeaders.AUTHORIZATION,
                        basicAuth
                    );
                    List<Header> hdrs = Arrays.asList(authHdr);
                    client
                        .getParams()
                        .setParameter(
                            ClientPNames.DEFAULT_HEADERS,
                            hdrs
                        );
                    authMethod = AuthMethod.Basic;
                }
            }
        }

        // CouchDB sends cookies with Expires attribute containing commas.
        // This violates strict spec and thus not compatible with
        // default BEST_MATCH policy.
        client
            .getParams()
            .setParameter(
                ClientPNames.COOKIE_POLICY, 
                CookiePolicy.BROWSER_COMPATIBILITY
             );
        return new Couch(client, section.getString("url"));
    }

    private synchronized DatabaseIndexer getIndexer(final Database database)
            throws IOException, JSONException {
        DatabaseIndexer result = indexers.get(database);
        Thread thread = threads.get(database);
        if (result == null || thread == null || !thread.isAlive()) {
            result = new DatabaseIndexer(client, root, database, ini);
            thread = new Thread(result);
            thread.start();
            result.awaitInitialization();
            if (result.isClosed()) {
                return null;
            } else {
                indexers.put(database, result);
                threads.put(database, thread);
            }
        }

        return result;
    }

    private DatabaseIndexer getIndexer(final HttpServletRequest req)
            throws IOException, JSONException {
        final Couch couch = getCouch(req);
        final Database database = couch.getDatabase(new PathParts(req)
                .getDatabaseName());
        return getIndexer(database);
    }

    private void handleWelcomeReq(final HttpServletRequest req,
                                  final HttpServletResponse resp) throws ServletException,
            IOException, JSONException {
        final Package p = this.getClass().getPackage();
        final JSONObject welcome = new JSONObject();
        welcome.put("couchdb-lucene", "Welcome");
        welcome.put("version", p.getImplementationVersion());
        ServletUtils.sendJson(req, resp, welcome);
    }

    @Override
    protected void doGet(final HttpServletRequest req,
                         final HttpServletResponse resp) throws ServletException,
            IOException {
        try {
            doGetInternal(req, resp);
        } catch (final JSONException e) {
            resp.sendError(500);
        }
    }

    private void doGetInternal(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException, JSONException {
        switch (StringUtils.countMatches(req.getRequestURI(), "/")) {
            case 1:
                handleWelcomeReq(req, resp);
                return;
            case 5:
                final DatabaseIndexer indexer = getIndexer(req);
                if (indexer == null) {
                    ServletUtils.sendJsonError(req, resp, 500, "error_creating_index");
                    return;
                }

                if (req.getParameter("q") == null) {
                    indexer.info(req, resp);
                } else {
                    indexer.search(req, resp);
                }
                return;
        }

        ServletUtils.sendJsonError(req, resp, 400, "bad_request");
    }

    @Override
    protected void doPost(final HttpServletRequest req,
                          final HttpServletResponse resp) throws ServletException,
            IOException {
        try {
            doPostInternal(req, resp);
        } catch (final JSONException e) {
            resp.sendError(500);
        }
    }

    private void doPostInternal(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, JSONException {
        switch (StringUtils.countMatches(req.getRequestURI(), "/")) {
            case 3:
                if (req.getPathInfo().endsWith("/_cleanup")) {
                    cleanup(req, resp);
                    return;
                }
                break;
            case 5: {
                final DatabaseIndexer indexer = getIndexer(req);
                if (indexer == null) {
                    ServletUtils.sendJsonError(req, resp, 500, "error_creating_index");
                    return;
                }
                indexer.search(req, resp);
                break;
            }
            case 6:
                final DatabaseIndexer indexer = getIndexer(req);
                indexer.admin(req, resp);
                return;
        }
        ServletUtils.sendJsonError(req, resp, 400, "bad_request");
    }

}
