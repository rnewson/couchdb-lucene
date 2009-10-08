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
import org.apache.log4j.Logger;

import com.github.rnewson.couchdb.lucene.util.StatusCodeResponseHandler;

/**
 * Communication with couchdb.
 * 
 * @author rnewson
 * 
 */
abstract class Couch {
    
    private static final Logger LOG = Logger.getLogger(Couch.class);

    static Couch getInstance(final HttpClient client, final String url) throws IOException {
        final HttpGet get = new HttpGet(url);
        final ResponseHandler<String> handler = new ResponseHandler<String>() {
            public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                return response.getFirstHeader("Server").getValue();
            }
        };
        final String server = client.execute(get, handler);
        if (server.contains("CouchDB/0.11")) {
            LOG.info("CouchDB 0.11 detected.");
            return new CouchV11(client, url);
        }
        if (server.contains("CouchDB/0.10")) {
            LOG.info("CouchDB 0.10 detected.");
            return new CouchV10(client, url);
        }
        throw new UnsupportedOperationException("No support for " + server);
    }

    private static final String[] EMPTY_ARR = new String[0];

    private final HttpClient httpClient;

    private final String url;

    protected Couch(final HttpClient httpClient, final String url) {
        this.httpClient = httpClient;
        this.url = url;
    }

    public boolean createDatabase(final String dbname) throws IOException {
        return put(Utils.urlEncode(dbname), null) == 201;
    }

    public boolean deleteDatabase(final String dbname) throws IOException {
        return delete(Utils.urlEncode(dbname)) == 201;
    }

    public String[] getAllDatabases() throws HttpException, IOException {
        return (String[]) JSONArray.fromObject(get("_all_dbs")).toArray(EMPTY_ARR);
    }

    public JSONObject getAllDocs(final String dbname, final String startkey, final String endkey) throws IOException {
        return JSONObject.fromObject(get(String.format("%s/_all_docs?startkey=%%22%s%%22&endkey=%%22%s%%22&include_docs=true",
                Utils.urlEncode(dbname), Utils.urlEncode(startkey), Utils.urlEncode(endkey))));
    }

    public JSONArray getAllDesignDocuments(final String dbname) throws IOException {
        return getAllDocs(dbname, "_design", "_design0").getJSONArray("rows");
    }

    public abstract JSONObject getChanges(final String dbname, final long since, final boolean includeDocs) throws IOException;

    public abstract JSONObject getChanges(final String dbname, final long since, final boolean includeDocs, final int limit)
            throws IOException;

    public JSONObject getDoc(final String dbname, final String id) throws IOException {
        return JSONObject.fromObject(get(String.format("%s/%s", Utils.urlEncode(dbname), id)));
    }

    public JSONObject getDocs(final String dbname, final String... ids) throws IOException {
        final JSONArray keys = new JSONArray();
        for (final String id : ids) {
            keys.add(id);
        }
        final JSONObject req = new JSONObject();
        req.element("keys", keys);

        return JSONObject
                .fromObject(post(String.format("%s/_all_docs?include_docs=true", Utils.urlEncode(dbname)), req.toString()));
    }

    public JSONObject getInfo(final String dbname) throws IOException {
        return JSONObject.fromObject(get(Utils.urlEncode(dbname)));
    }

    public boolean saveDocument(final String dbname, final String id, final String body) throws IOException {
        return put(String.format("%s/%s", Utils.urlEncode(dbname), id), body) == 201;
    }

    private int delete(final String path) throws IOException {
        final HttpDelete delete = new HttpDelete(url(path));
        return httpClient.execute(delete, new StatusCodeResponseHandler());
    }

    private String execute(final HttpUriRequest request) throws IOException {
        return httpClient.execute(request, new BasicResponseHandler());
    }

    protected final String get(final String path) throws IOException {
        return execute(new HttpGet(url(path)));
    }

    private String post(final String path, final String body) throws IOException {
        final HttpPost post = new HttpPost(url(path));
        post.setEntity(new StringEntity(body));
        return execute(post);
    }

    private int put(final String path, final String body) throws IOException {
        final HttpPut put = new HttpPut(url(path));
        if (body != null) {
            put.setHeader("Content-Type", Constants.CONTENT_TYPE);
            put.setEntity(new StringEntity(body));
        }
        return httpClient.execute(put, new StatusCodeResponseHandler());
    }

    String url(final String path) {
        return String.format("%s/%s", url, path);
    }

}

final class CouchV10 extends Couch {

    public CouchV10(HttpClient httpClient, String url) {
        super(httpClient, url);
    }

    public JSONObject getChanges(final String dbname, final long since, final boolean includeDocs) throws IOException {
        return JSONObject.fromObject(get(String.format("%s/_all_docs_by_seq?startkey=%d&include_docs=%b", Utils.urlEncode(dbname),
                since, includeDocs)));
    }

    public JSONObject getChanges(final String dbname, final long since, final boolean includeDocs, final int limit)
            throws IOException {
        return JSONObject.fromObject(get(String.format("%s/_all_docs_by_seq?startkey=%d&include_docs=%b&limit=%d", Utils
                .urlEncode(dbname), since, includeDocs, limit)));
    }

}

final class CouchV11 extends Couch {

    public CouchV11(HttpClient httpClient, String url) {
        super(httpClient, url);
    }

    public JSONObject getChanges(final String dbname, final long since, final boolean includeDocs) throws IOException {
        return JSONObject.fromObject(get(String.format("%s/_changes?since=%d&include_docs=%b", Utils.urlEncode(dbname), since,
                includeDocs)));
    }

    public JSONObject getChanges(final String dbname, final long since, final boolean includeDocs, final int limit)
            throws IOException {
        return JSONObject.fromObject(get(String.format("%s/_changes?since=%d&include_docs=%b&limit=%d", Utils.urlEncode(dbname),
                since, includeDocs, limit)));
    }

}
