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

import com.github.rnewson.couchdb.lucene.util.Utils;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class Database {

    private final HttpClient httpClient;

    private final String url;

    public Database(final HttpClient httpClient, final String url) {
        this.httpClient = httpClient;
        this.url = url.endsWith("/") ? url : url + "/";
    }

    public boolean create() throws IOException {
        return HttpUtils.put(httpClient, url, null) == 201;
    }

    public boolean delete() throws IOException {
        return HttpUtils.delete(httpClient, url) == 200;
    }

    public List<DesignDocument> getAllDesignDocuments() throws IOException, JSONException {
        final String body = HttpUtils.get(httpClient, String
                .format("%s_all_docs?startkey=%s&endkey=%s&include_docs=true",
                        url, Utils.urlEncode("\"_design\""), Utils
                        .urlEncode("\"_design0\"")));
        final JSONObject json = new JSONObject(body);
        return toDesignDocuments(json);
    }

    public CouchDocument getDocument(final String id) throws IOException, JSONException {
        final String response = HttpUtils.get(httpClient, url
                + Utils.urlEncode(id));
        return new CouchDocument(new JSONObject(response));
    }

    public DesignDocument getDesignDocument(final String id) throws IOException, JSONException {
        final String response = HttpUtils.get(httpClient, url
                + Utils.urlEncode(id));
        return new DesignDocument(new JSONObject(response));
    }

    public List<CouchDocument> getDocuments(final String... ids)
            throws IOException, JSONException {
        if (ids.length == 0) {
            return Collections.emptyList();
        }

        final JSONArray keys = new JSONArray();
        for (final String id : ids) {
            assert id != null;
            keys.put(id);
        }
        final JSONObject req = new JSONObject();
        req.put("keys", keys);

        final String body = HttpUtils.post(httpClient, url
                + "_all_docs?include_docs=true", req);
        return toDocuments(new JSONObject(body));
    }

    public DatabaseInfo getInfo() throws IOException, JSONException {
        return new DatabaseInfo(new JSONObject(HttpUtils.get(httpClient,
                url)));
    }

    public UpdateSequence getLastSequence() throws IOException, JSONException {
        final JSONObject result = new JSONObject(HttpUtils.get(httpClient, url
                + "_changes?limit=0&descending=true"));
        return UpdateSequence.parseUpdateSequence(result.getString("last_seq"));
    }

    public <T> T handleAttachment(final String doc, final String att,
                                  final ResponseHandler<T> handler) throws IOException {
        final HttpGet get = new HttpGet(url + "/" + Utils.urlEncode(doc) + "/"
                + Utils.urlEncode(att));
        return httpClient.execute(get, handler);
    }

    public HttpUriRequest getChangesRequest(final UpdateSequence since, final long timeout)
            throws IOException {
        final String uri;
        if (timeout > -1) {
            uri = url + "_changes?feed=continuous&timeout="+timeout+"&include_docs=true";
        } else {
            uri = url + "_changes?feed=continuous&heartbeat=15000&include_docs=true";
        }
        return new HttpGet(since.appendSince(uri));
    }

    public boolean saveDocument(final String id, final String body)
            throws IOException {
        return HttpUtils.put(httpClient, url + Utils.urlEncode(id), body) == 201;
    }

    public UUID getUuid() throws IOException, JSONException {
        try {
            final CouchDocument local = getDocument("_local/lucene");
            return UUID.fromString(local.asJson().getString("uuid"));
        } catch (final HttpResponseException e) {
            switch (e.getStatusCode()) {
                case HttpStatus.SC_NOT_FOUND:
                    return null;
                default:
                    throw e;
            }
        }
    }

    public void createUuid() throws IOException {
        final UUID uuid = UUID.randomUUID();
        saveDocument("_local/lucene", String.format("{\"uuid\":\"%s\"}", uuid));
    }

    public UUID getOrCreateUuid() throws IOException, JSONException {
        final UUID result = getUuid();
        if (result != null) {
            return result;
        }
        createUuid();
        return getUuid();
    }

    private List<DesignDocument> toDesignDocuments(final JSONObject json) throws JSONException {
        final List<DesignDocument> result = new ArrayList<>();
        for (final JSONObject doc : rows(json)) {
            result.add(new DesignDocument(doc));
        }
        return result;
    }

    private List<CouchDocument> toDocuments(final JSONObject json) throws JSONException {
        final List<CouchDocument> result = new ArrayList<>();
        for (final JSONObject doc : rows(json)) {
            result.add(doc == null ? null : new CouchDocument(doc));
        }
        return result;
    }

    private List<JSONObject> rows(final JSONObject json) throws JSONException {
        final List<JSONObject> result = new ArrayList<>();
        final JSONArray rows = json.getJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) {
            result.add(rows.getJSONObject(i).optJSONObject("doc"));
        }
        return result;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((url == null) ? 0 : url.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Database other = (Database) obj;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Database [url=" + url + "]";
    }

}
