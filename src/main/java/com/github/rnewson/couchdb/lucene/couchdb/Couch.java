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

package com.github.rnewson.couchdb.lucene.couchdb;

import org.apache.http.client.HttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Simple Java API access to CouchDB.
 *
 * @author rnewson
 */
public class Couch {

    private final HttpClient httpClient;
    private final String url;

    public Couch(final HttpClient httpClient, final String url)
            throws IOException {
        this.httpClient = httpClient;
        this.url = url.endsWith("/") ? url : url + "/";
    }

    public final JSONArray getAllDatabases() throws IOException, JSONException {
        final String response = HttpUtils.get(httpClient, url + "_all_dbs");
        return new JSONArray(response);
    }

    public final JSONObject getInfo() throws IOException, JSONException {
        return new JSONObject(HttpUtils.get(httpClient, url));
    }

    public Database getDatabase(final String dbname) throws IOException {
        return new Database(httpClient, url + dbname);
    }

}
