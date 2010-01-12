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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.lucene.index.IndexWriter;

import com.github.rnewson.couchdb.lucene.Lucene.WriterCallback;
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

        if (path == null) {
            ServletUtils.sendJSONError(req, resp, 400, "Bad path");
            return;
        }
        lucene.startIndexing(path, true);

        String command = req.getPathInfo();
        command = command.substring(command.lastIndexOf("/") + 1);

        if ("_expunge".equals(command)) {
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

}
