package com.github.rnewson.couchdb.lucene.couchdb;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;

import com.github.rnewson.couchdb.lucene.util.Utils;

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

    public List<DesignDocument> getAllDesignDocuments() throws IOException {
        final String body = HttpUtils.get(httpClient, String.format("%s_all_docs?startkey=%s&endkey=%s&include_docs=true", url,
                Utils.urlEncode("\"_design\""), Utils.urlEncode("\"_design0\"")));
        final JSONObject json = JSONObject.fromObject(body);
        return toDesignDocuments(json);
    }

    public CouchDocument getDocument(final String id) throws IOException {
        final String response = HttpUtils.get(httpClient, url + Utils.urlEncode(id));
        return new CouchDocument(JSONObject.fromObject(response));
    }

    public DesignDocument getDesignDocument(final String id) throws IOException {
        final String response = HttpUtils.get(httpClient, url + "_design/" + Utils.urlEncode(id));
        return new DesignDocument(JSONObject.fromObject(response));
    }

    public List<CouchDocument> getDocuments(final String... ids) throws IOException {
        final JSONArray keys = new JSONArray();
        for (final String id : ids) {
            keys.add(id);
        }
        final JSONObject req = new JSONObject();
        req.element("keys", keys);

        final String body = HttpUtils.post(httpClient, url + "_all_docs?include_docs=true", req.toString());
        final JSONObject json = JSONObject.fromObject(body);
        return toDocuments(json);
    }

    public JSONObject getInfo() throws IOException {
        return JSONObject.fromObject(HttpUtils.get(httpClient, url));
    }

    public <T> T handleAttachment(final String doc, final String att, final ResponseHandler<T> handler) throws IOException {
        final HttpGet get = new HttpGet(url + "/" + Utils.urlEncode(doc) + "/" + Utils.urlEncode(att));
        return httpClient.execute(get, handler);
    }

    public HttpUriRequest getChangesRequest(final long since) throws IOException {
        return new HttpGet(url + "_changes?feed=continuous&heartbeat=15000&include_docs=true&since=" + since);
    }

    public boolean saveDocument(final String id, final String body) throws IOException {
        return HttpUtils.put(httpClient, url + Utils.urlEncode(id), body) == 201;
    }

    public UUID getUuid() throws IOException {
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

    private List<DesignDocument> toDesignDocuments(final JSONObject json) {
        final List<DesignDocument> result = new ArrayList<DesignDocument>();
        for (final JSONObject doc : rows(json)) {
            result.add(new DesignDocument(doc));
        }
        return result;
    }

    private List<CouchDocument> toDocuments(final JSONObject json) {
        final List<CouchDocument> result = new ArrayList<CouchDocument>();
        for (final JSONObject doc : rows(json)) {
            result.add(new CouchDocument(doc));
        }
        return result;
    }

    private List<JSONObject> rows(final JSONObject json) {
        final List<JSONObject> result = new ArrayList<JSONObject>();
        final JSONArray rows = json.getJSONArray("rows");
        for (int i = 0; i < rows.size(); i++) {
            result.add(rows.getJSONObject(i).getJSONObject("doc"));
        }
        return result;
    }

}
