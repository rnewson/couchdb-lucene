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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;

/**
 * Communication with couchdb.
 * 
 * @author rnewson
 * 
 */
public final class Database {

    private static final String[] EMPTY_ARR = new String[0];

    private static final ResponseHandler<String> RESPONSE_BODY_HANDLER = new BasicResponseHandler();

    private static final ResponseHandler<Integer> STATUS_CODE_HANDLER = new ResponseHandler<Integer>() {
        @Override
        public Integer handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
            return response.getStatusLine().getStatusCode();
        }
    };

    private String url;

    private HttpClient httpClient;

    public Database() {
    }

    public void setHttpClient(final HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setUrl(final String url) {
        if (url.endsWith("/"))
            this.url = url.substring(0, url.length() - 1);
        else
            this.url = url;
    }

    public String[] getAllDatabases() throws HttpException, IOException {
        return (String[]) JSONArray.fromObject(get("_all_dbs")).toArray(EMPTY_ARR);
    }

    public JSONObject getAllDocsBySeq(final String dbname, final long startkey) throws IOException {
        return JSONObject.fromObject(get(String.format("%s/_all_docs_by_seq?startkey=%s&include_docs=true",
                encode(dbname), startkey)));
    }

    public boolean createDatabase(final String dbname) throws IOException {
        return put(encode(dbname), null) == 201;
    }

    public boolean deleteDatabase(final String dbname) throws IOException {
        return delete(encode(dbname)) == 201;
    }

    public boolean saveDocument(final String dbname, final String id, final String body) throws IOException {
        return put(String.format("%s/%s", encode(dbname), id), body) == 201;
    }

    public JSONObject getAllDocs(final String dbname, final String startkey, final String endkey) throws IOException {
        return JSONObject.fromObject(get(String.format(
                "%s/_all_docs?startkey=%%22%s%%22&endkey=%%22%s%%22&include_docs=true", encode(dbname),
                encode(startkey), encode(endkey))));
    }

    public JSONObject getAllDocsBySeq(final String dbname, final long startkey, final int limit) throws IOException {
        return JSONObject.fromObject(get(String.format("%s/_all_docs_by_seq?startkey=%d&limit=%d&include_docs=true",
                encode(dbname), startkey, limit)));
    }

    public JSONObject getDoc(final String dbname, final String id) throws IOException {
        return JSONObject.fromObject(get(String.format("%s/%s", encode(dbname), id)));
    }

    public JSONObject getDocs(final String dbname, final String... ids) throws IOException {
        final JSONArray keys = new JSONArray();
        for (final String id : ids) {
            keys.add(id);
        }
        final JSONObject req = new JSONObject();
        req.element("keys", keys);

        return JSONObject.fromObject(post(String.format("%s/_all_docs?include_docs=true", encode(dbname)), req
                .toString()));
    }

    public JSONObject getInfo(final String dbname) throws IOException {
        return JSONObject.fromObject(get(encode(dbname)));
    }

    private String get(final String path) throws IOException {
        return execute(new HttpGet(url(path)));
    }

    String url(final String path) {
        return String.format("%s/%s", url, path);
    }

    String encode(final String path) {
        try {
            return URLEncoder.encode(path, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            throw new Error("UTF-8 support missing!");
        }
    }

    private String post(final String path, final String body) throws IOException {
        final HttpPost post = new HttpPost(url(path));
        post.setEntity(new StringEntity(body));
        return execute(post);
    }

    private int put(final String path, final String body) throws IOException {
        final HttpPut put = new HttpPut(url(path));
        if (body != null) {
            put.setHeader("Content-Type", "application/json");
            put.setEntity(new StringEntity(body));
        }
        return httpClient.execute(put, STATUS_CODE_HANDLER);
    }

    private int delete(final String path) throws IOException {
        final HttpDelete delete = new HttpDelete(url(path));
        return httpClient.execute(delete, STATUS_CODE_HANDLER);
    }

    private String execute(final HttpUriRequest request) throws IOException {
        return httpClient.execute(request, RESPONSE_BODY_HANDLER);
    }

}
