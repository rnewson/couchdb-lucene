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
import java.util.Scanner;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;

/**
 * Search entry point.
 */
public final class Search {

    public static void main(final String[] args) {
        Utils.LOG.info("searcher started.");
        try {
            IndexReader reader = null;
            IndexSearcher searcher = null;

            final Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                if (reader == null) {
                    // Open a reader and searcher if index exists.
                    if (IndexReader.indexExists(Config.INDEX_DIR)) {
                        reader = IndexReader.open(NIOFSDirectory.getDirectory(Config.INDEX_DIR), true);
                        searcher = new IndexSearcher(reader);
                    }
                }

                final String line = scanner.nextLine();

                // Process search request if index exists.
                if (searcher == null) {
                    System.out.println(Utils.error(503, "couchdb-lucene not available."));
                    continue;
                }

                final JSONObject obj;
                try {
                    obj = JSONObject.fromObject(line);
                } catch (final JSONException e) {
                    System.out.println(Utils.error(400, "invalid JSON."));
                    continue;
                }

                if (!obj.has("query")) {
                    System.out.println(Utils.error(400, "No query found in request."));
                    continue;
                }

                final JSONObject query = obj.getJSONObject("query");

                final boolean reopen = !"ok".equals(query.optString("stale", "not-ok"));

                // Refresh reader and searcher if necessary.
                if (reader != null && reopen) {
                    final IndexReader newReader = reader.reopen();
                    if (reader != newReader) {
                        Utils.LOG.info("Lucene index was updated, reopening searcher.");
                        final IndexReader oldReader = reader;
                        reader = newReader;
                        searcher = new IndexSearcher(reader);
                        oldReader.close();
                    }
                }

                try {
                    // A query.
                    if (query.has("q")) {
                        final SearchRequest request = new SearchRequest(obj);
                        final String result = request.execute(searcher);
                        System.out.println(result);
                        continue;
                    }
                    // info.
                    if (query.keySet().isEmpty()) {
                        final JSONObject json = new JSONObject();
                        json.put("current", reader.isCurrent());
                        json.put("disk_size", size(reader.directory()));
                        json.put("doc_count", reader.numDocs());
                        json.put("doc_del_count", reader.numDeletedDocs());
                        final JSONArray fields = new JSONArray();
                        for (final Object field : reader.getFieldNames(FieldOption.INDEXED)) {
                            if (((String) field).startsWith("_"))
                                continue;
                            fields.add(field);
                        }
                        json.put("fields", fields);
                        json.put("last_modified", IndexReader.lastModified(Config.INDEX_DIR));
                        json.put("optimized", reader.isOptimized());

                        final JSONObject info = new JSONObject();
                        info.put("code", 200);
                        info.put("json", json);
                        final JSONObject headers = new JSONObject();
                        headers.put("Content-Type", "text/plain");
                        info.put("headers", headers);

                        System.out.println(info);
                    }
                } catch (final Exception e) {
                    System.out.println(Utils.error(400, e));
                }

                System.out.println(Utils.error(400, "Bad request."));
            }
            if (reader != null) {
                reader.close();
            }
        } catch (final Exception e) {
            System.out.println(Utils.error(500, e.getMessage()));
        }
        Utils.LOG.info("searcher stopped.");
    }

    private static long size(final Directory dir) throws IOException {
        long result = 0;
        for (final String name : dir.list()) {
            result += dir.fileLength(name);
        }
        return result;
    }

}
