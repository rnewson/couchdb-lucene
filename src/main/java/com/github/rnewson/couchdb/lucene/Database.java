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
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.io.IOUtils;

/**
 * Communication with couchdb.
 * 
 * @author rnewson
 * 
 */
public final class Database {

    static final HttpClient CLIENT = new HttpClient();

    private static final String[] EMPTY_ARR = new String[0];

    static {
        CLIENT.getParams().setCookiePolicy(CookiePolicy.IGNORE_COOKIES);
        if (Config.DB_USER != null && Config.DB_PASSWORD != null) {
            CLIENT.getParams().setAuthenticationPreemptive(true);
            final Credentials creds = new UsernamePasswordCredentials(Config.DB_USER, Config.DB_PASSWORD);
            CLIENT.getState().setCredentials(AuthScope.ANY, creds);
            Utils.LOG.debug("Authenticating to couchdb as " + Config.DB_USER);
        }
    }

    private final String url;

    public Database(final String url) {
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
        return execute(new GetMethod(url(path)));
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
        final PostMethod post = new PostMethod(url(path));
        post.setRequestEntity(new StringRequestEntity(body, "application/json", "UTF-8"));
        return execute(post);
    }

    private int put(final String path, final String body) throws IOException {
        final PutMethod method = new PutMethod(url(path));
        if (body != null) {
            method.setRequestEntity(new StringRequestEntity(body, "application/json", "UTF-8"));
        }
        try {
            return CLIENT.executeMethod(method);
        } finally {
            method.releaseConnection();
        }
    }

    private int delete(final String path) throws IOException {
        final DeleteMethod method = new DeleteMethod(url(path));
        try {
            return CLIENT.executeMethod(method);
        } finally {
            method.releaseConnection();
        }
    }

    private String execute(final HttpMethodBase method) throws IOException {
        try {
            final int sc = CLIENT.executeMethod(method);
            if (sc < 200 || sc > 299) {
                throw new HttpException("Unexpected status code: " + sc);
            }
            final InputStream in = method.getResponseBodyAsStream();
            try {
                final StringWriter writer = new StringWriter(2048);
                IOUtils.copy(in, writer, method.getResponseCharSet());
                return writer.toString();
            } finally {
                in.close();
            }
        } finally {
            method.releaseConnection();
        }
    }

}
