package com.github.rnewson.couchdb.lucene.couchdb;

import java.io.IOException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;

import com.github.rnewson.couchdb.lucene.util.Utils;

public final class Database {

    private final HttpClient httpClient;

    private final String url;

    public Database(final HttpClient httpClient, final String url) {
        this.httpClient = httpClient;
        this.url = url.endsWith("/") ? url : url + "/";
    }

    public final boolean create() throws IOException {
        return HttpUtils.put(httpClient, url, null) == 201;
    }

    public final boolean delete() throws IOException {
        return HttpUtils.delete(httpClient, url) == 200;
    }

    public JSONArray getAllDesignDocuments() throws IOException {
        return getDocuments("_design", "_design0").getJSONArray("rows");
    }

    public final JSONObject getDocument(final String id) throws IOException {
        final String response = HttpUtils.get(httpClient, url + Utils.urlEncode(id));
        return JSONObject.fromObject(response);
    }

    public final JSONObject getDocuments(final String... ids) throws IOException {
        final JSONArray keys = new JSONArray();
        for (final String id : ids) {
            keys.add(id);
        }
        final JSONObject req = new JSONObject();
        req.element("keys", keys);

        final String response = HttpUtils.post(httpClient, url + "_all_docs?include_docs=true", req.toString());
        return JSONObject.fromObject(response);
    }

    public final JSONObject getDocuments(final String startkey, final String endkey) throws IOException {
        return JSONObject.fromObject(HttpUtils.get(httpClient, String.format(
                "%s_all_docs?startkey=%%22%s%%22&endkey=%%22%s%%22&include_docs=true", url, Utils.urlEncode(startkey), Utils
                        .urlEncode(endkey))));
    }

    public final JSONObject getInfo() throws IOException {
        return JSONObject.fromObject(HttpUtils.get(httpClient, url));
    }

    public final <T> T handleAttachment(final String doc, final String att, final ResponseHandler<T> handler) throws IOException {
        final HttpGet get = new HttpGet(url + "/" + Utils.urlEncode(doc) + "/" + Utils.urlEncode(att));
        return httpClient.execute(get, handler);
    }

    public <T> T handleChanges(final long since, final ResponseHandler<T> handler) throws IOException {
        final HttpGet get = new HttpGet(url + "/_changes?feed=continuous&heartbeat=15000&include_docs=true&since=" + since);
        return httpClient.execute(get, handler);
    }

    public final boolean saveDocument(final String id, final String body) throws IOException {
        return HttpUtils.put(httpClient, url + Utils.urlEncode(id), body) == 201;
    }

}
