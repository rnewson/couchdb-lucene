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

import static com.github.rnewson.couchdb.lucene.util.ServletUtils.getBooleanParameter;
import static com.github.rnewson.couchdb.lucene.util.ServletUtils.getIntParameter;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.http.client.HttpClient;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.util.Version;

import com.github.rnewson.couchdb.lucene.Lucene.SearcherCallback;
import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.util.Constants;
import com.github.rnewson.couchdb.lucene.util.IndexPath;
import com.github.rnewson.couchdb.lucene.util.ServletUtils;
import com.github.rnewson.couchdb.lucene.util.StopWatch;
import com.github.rnewson.couchdb.lucene.util.Utils;

/**
 * Perform queries against local indexes.
 * 
 * @author rnewson
 * 
 */
public final class SearchServlet extends HttpServlet {

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
        // q is mandatory.
        if (req.getParameter("q") == null) {
            ServletUtils.sendJSONError(req, resp, 400, "Missing q attribute.");
            return;
        }

        final boolean debug = getBooleanParameter(req, "debug");
        final boolean rewrite_query = getBooleanParameter(req, "rewrite");
        final boolean staleOk = Utils.getStaleOk(req);

        final IndexPath path = IndexPath.parse(configuration, req);

        if (path == null) {
            ServletUtils.sendJSONError(req, resp, 400, "Bad path");
            return;
        }

        final ViewIndexer indexer = lucene.startIndexing(path, staleOk);

        final SearcherCallback callback = new SearcherCallback() {

            public void callback(final IndexSearcher searcher, final String version) throws IOException {
                // Check for 304 - Not Modified.
                if (!debug && version.equals(req.getHeader("If-None-Match"))) {
                    resp.setStatus(304);
                    return;
                }

                // Parse query.
                final Analyzer analyzer = indexer.getAnalyzer();
                final CustomQueryParser parser = new CustomQueryParser(Version.LUCENE_CURRENT, Constants.DEFAULT_FIELD, analyzer);

                final String[] queries = req.getParameterValues("q");
                final JSONArray arr = new JSONArray();

                for (final String query : queries) {
                    try {
                        arr.add(performQuery(debug, rewrite_query, searcher, version, parser.parse(query)));
                    } catch (final ParseException e) {
                        ServletUtils.sendJSONError(req, resp, 400, "Bad query syntax: " + e.getMessage());
                        return;
                    }
                }

                Utils.setResponseContentTypeAndEncoding(req, resp);

                // Cache-related headers.
                resp.setHeader("ETag", version);
                resp.setHeader("Cache-Control", "must-revalidate");

                final JSON json = arr.size() > 1 ? arr : arr.getJSONObject(0);

                // Format response body.
                final String callback = req.getParameter("callback");
                final String body;
                if (callback != null) {
                    body = String.format("%s(%s)", callback, json);
                } else {
                    body = json.toString(debug ? 2 : 0);
                }

                final Writer writer = resp.getWriter();
                try {
                    writer.write(body);
                } finally {
                    writer.close();
                }
            }

            private JSONObject performQuery(final boolean debug, final boolean rewrite_query, final IndexSearcher searcher,
                    final String version, final Query q) throws IOException, CorruptIndexException {
                final JSONObject result = new JSONObject();
                result.put("q", q.toString());
                if (debug) {
                    result.put("analyzer", indexer.getAnalyzer().getClass().getSimpleName());
                    result.put("plan", QueryPlan.toPlan(q));
                }
                result.put("etag", version);

                if (rewrite_query) {
                    final Query rewritten_q = q.rewrite(searcher.getIndexReader());
                    result.put("rewritten_q", rewritten_q.toString());

                    final JSONObject freqs = new JSONObject();

                    final Set<Term> terms = new HashSet<Term>();
                    rewritten_q.extractTerms(terms);
                    for (final Object term : terms) {
                        final int freq = searcher.docFreq((Term) term);
                        freqs.put(term, freq);
                    }
                    result.put("freqs", freqs);
                } else {
                    // Perform the search.
                    final TopDocs td;
                    final StopWatch stopWatch = new StopWatch();

                    final boolean include_docs = getBooleanParameter(req, "include_docs");
                    final int limit = getIntParameter(req, "limit", 25);
                    final Sort sort = CustomQueryParser.toSort(req.getParameter("sort"));
                    final int skip = getIntParameter(req, "skip", 0);

                    if (sort == null) {
                        td = searcher.search(q, null, skip + limit);
                    } else {
                        td = searcher.search(q, null, skip + limit, sort);
                    }
                    stopWatch.lap("search");

                    // Fetch matches (if any).
                    final int max = max(0, min(td.totalHits - skip, limit));
                    final JSONArray rows = new JSONArray();
                    final String[] fetch_ids = new String[max];
                    for (int i = skip; i < skip + max; i++) {
                        final Document doc = searcher.doc(td.scoreDocs[i].doc);
                        final JSONObject row = new JSONObject();
                        final JSONObject fields = new JSONObject();

                        // Include stored fields.
                        for (final Object f : doc.getFields()) {
                            final Field fld = (Field) f;

                            if (!fld.isStored()) {
                                continue;
                            }
                            final String name = fld.name();
                            final String value = fld.stringValue();
                            if (value != null) {
                                if ("_id".equals(name)) {
                                    row.put("id", value);
                                } else {
                                    if (!fields.has(name)) {
                                        fields.put(name, value);
                                    } else {
                                        final Object obj = fields.get(name);
                                        if (obj instanceof String) {
                                            final JSONArray arr = new JSONArray();
                                            arr.add(obj);
                                            arr.add(value);
                                            fields.put(name, arr);
                                        } else {
                                            assert obj instanceof JSONArray;
                                            ((JSONArray) obj).add(value);
                                        }
                                    }
                                }
                            }
                        }

                        if (!Float.isNaN(td.scoreDocs[i].score)) {
                            row.put("score", td.scoreDocs[i].score);
                        }
                        // Include sort order (if any).
                        if (td instanceof TopFieldDocs) {
                            final FieldDoc fd = (FieldDoc) ((TopFieldDocs) td).scoreDocs[i];
                            row.put("sort_order", fd.fields);
                        }
                        // Fetch document (if requested).
                        if (include_docs) {
                            fetch_ids[i - skip] = doc.get("_id");
                        }
                        if (fields.size() > 0) {
                            row.put("fields", fields);
                        }
                        rows.add(row);
                    }
                    // Fetch documents (if requested).
                    if (include_docs && fetch_ids.length > 0) {
                        final HttpClient httpClient = HttpClientFactory.getInstance();
                        try {
                            final Couch couch = Couch.getInstance(httpClient, path.getUrl());
                            final Database database = couch.getDatabase(path.getDatabase());
                            final JSONArray fetched_docs = database.getDocuments(fetch_ids).getJSONArray("rows");
                            for (int i = 0; i < max; i++) {
                                rows.getJSONObject(i).put("doc", fetched_docs.getJSONObject(i).getJSONObject("doc"));
                            }
                        } finally {
                            httpClient.getConnectionManager().shutdown();
                        }
                    }
                    stopWatch.lap("fetch");

                    result.put("skip", skip);
                    result.put("limit", limit);
                    result.put("total_rows", td.totalHits);
                    result.put("search_duration", stopWatch.getElapsed("search"));
                    result.put("fetch_duration", stopWatch.getElapsed("fetch"));
                    // Include sort info (if requested).
                    if (td instanceof TopFieldDocs) {
                        result.put("sort_order", CustomQueryParser.toString(((TopFieldDocs) td).fields));
                    }
                    result.put("rows", rows);
                }
                return result;
            }

            public void onMissing() throws IOException {
                ServletUtils.sendJSONError(req, resp, 404, "Index for " + path + " is missing.");
            }
        };

        lucene.withSearcher(path, staleOk, callback);
    }
}
