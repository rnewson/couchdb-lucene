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
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.HttpClient;
import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexWriter;

import com.github.rnewson.couchdb.lucene.Lucene.WriterCallback;
import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.couchdb.DesignDocument;
import com.github.rnewson.couchdb.lucene.couchdb.View;
import com.github.rnewson.couchdb.lucene.util.IndexPath;
import com.github.rnewson.couchdb.lucene.util.ServletUtils;
import com.github.rnewson.couchdb.lucene.util.Utils;

/**
 * Administrative functions.
 * 
 * <ul>
 * <li>_expunge
 * <li>_optimize
 * <li>_pause
 * <li>_resume
 * </ul>
 * 
 * @author rnewson
 * 
 */
public final class AdminServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final JSONObject JSON_SUCCESS = JSONObject.fromObject("{\"ok\":true}");

    private static final Logger LOG = Logger.getLogger(AdminServlet.class);

    private Lucene lucene;

    private HierarchicalINIConfiguration configuration;

    public void setLucene(final Lucene lucene) {
        this.lucene = lucene;
    }

    public void setConfiguration(final HierarchicalINIConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final IndexPath path = IndexPath.parse(configuration, req);
        String command = req.getPathInfo();
        command = command.substring(command.lastIndexOf("/") + 1);
        if (path == null) {
            final String[] parts = IndexPath.parts(req);
            if (parts.length == 3) {
                if ("_cleanup".equals(command)) {
                    cleanup(parts[0]);
                    resp.setStatus(202);
                    Utils.writeJSON(resp, JSON_SUCCESS);
                }
            } else {
                ServletUtils.sendJSONError(req, resp, 400, "Bad path");
            }
            return;
        }

        lucene.startIndexing(path, true);

        if ("_expunge".equals(command)) {
            LOG.info("Expunging deletes from " + path);
            lucene.withWriter(path, new WriterCallback() {
                public boolean callback(final IndexWriter writer) throws IOException {
                    writer.expungeDeletes(false);
                    return false;
                }

                public void onMissing() throws IOException {
                    resp.sendError(404);
                }
            });
            Utils.setResponseContentTypeAndEncoding(req, resp);
            resp.setStatus(202);
            Utils.writeJSON(resp, JSON_SUCCESS);
            return;
        }

        if ("_optimize".equals(command)) {
            LOG.info("Optimizing " + path);
            lucene.withWriter(path, new WriterCallback() {
                public boolean callback(final IndexWriter writer) throws IOException {
                    writer.optimize(false);
                    return false;
                }

                public void onMissing() throws IOException {
                    resp.sendError(404);
                }
            });
            Utils.setResponseContentTypeAndEncoding(req, resp);
            resp.setStatus(202);
            Utils.writeJSON(resp, JSON_SUCCESS);
            return;
        }

        resp.sendError(400, "Bad request");
    }

    private void cleanup(final String key) throws IOException {
        final HttpClient client = HttpClientFactory.getInstance();
        final Couch couch = Couch.getInstance(client, IndexPath.url(configuration, key));

        final Set<String> dbKeep = new HashSet<String>();

        for (final String dbname : couch.getAllDatabases()) {
            final Database db = couch.getDatabase(dbname);
            final UUID uuid = db.getUuid();
            if (uuid == null) {
                continue;
            }

            dbKeep.add(uuid.toString());

            final Set<String> viewKeep = new HashSet<String>();
            for (final DesignDocument ddoc : db.getAllDesignDocuments()) {
                for (final View view : ddoc.getAllViews()) {
                    viewKeep.add(view.getDigest());
                }
            }
            // Delete all indexes except the keepers.
            for (final File dir : lucene.getUuidDir(db.getUuid()).listFiles()) {
                if (!viewKeep.contains(dir.getName())) {
                    LOG.info("Cleaning old index at " + dir);
                    FileUtils.deleteDirectory(dir);
                }
            }
        }

        // Delete all directories except the keepers.
        for (final File dir : lucene.getRootDir().listFiles()) {
            if (!dbKeep.contains(dir.getName())) {
                LOG.info("Cleaning old index at " + dir);
                FileUtils.deleteDirectory(dir);
            }
        }
    }
}
