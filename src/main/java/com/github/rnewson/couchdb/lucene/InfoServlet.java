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
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReader.FieldOption;

import com.github.rnewson.couchdb.lucene.Lucene.ReaderCallback;
import com.github.rnewson.couchdb.lucene.util.IndexPath;
import com.github.rnewson.couchdb.lucene.util.ServletUtils;
import com.github.rnewson.couchdb.lucene.util.Utils;

/**
 * Provides information current indexes.
 * 
 * @author rnewson
 * 
 */
public class InfoServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private Lucene lucene;

    private HierarchicalINIConfiguration configuration;

    public void setLucene(final Lucene lucene) {
        this.lucene = lucene;
    }

    public void setConfiguration(final HierarchicalINIConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final IndexPath path = IndexPath.parse(configuration, req);

        if (path == null) {
            ServletUtils.sendJSONError(req, resp, 400, "Bad path");
            return;
        }
        lucene.startIndexing(path, true);

        lucene.withReader(path, Utils.getStaleOk(req), new ReaderCallback() {
            public void callback(final IndexReader reader) throws IOException {
                final JSONObject result = new JSONObject();
                result.put("current", reader.isCurrent());
                result.put("disk_size", Utils.directorySize(reader.directory()));
                result.put("doc_count", reader.numDocs());
                result.put("doc_del_count", reader.numDeletedDocs());
                final JSONArray fields = new JSONArray();
                for (final Object field : reader.getFieldNames(FieldOption.INDEXED)) {
                    if (((String) field).startsWith("_")) {
                        continue;
                    }
                    fields.add(field);
                }
                result.put("fields", fields);
                result.put("last_modified", Long.toString(IndexReader.lastModified(reader.directory())));
                result.put("optimized", reader.isOptimized());
                result.put("ref_count", reader.getRefCount());

                final JSONObject info = new JSONObject();
                info.put("code", 200);
                info.put("json", result);

                Utils.setResponseContentTypeAndEncoding(req, resp);
                final Writer writer = resp.getWriter();
                try {
                    writer.write(result.toString());
                } finally {
                    writer.close();
                }
            }

            public void onMissing() throws IOException {
                resp.sendError(404);
            }
        });
    }

}
