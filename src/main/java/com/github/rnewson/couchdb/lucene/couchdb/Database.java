package com.github.rnewson.couchdb.lucene.couchdb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;

import com.github.rnewson.couchdb.lucene.util.Utils;

public abstract class Database {

    public static final class V10 extends Database {

        public V10(final HttpClient httpClient, final String url) {
            super(httpClient, url);
        }

        public Action handleChanges(final long since, final ChangesHandler changesHandler) throws IOException {
            final int limit = 100;
            final ResponseHandler<Action> responseHandler = new ResponseHandler<Action>() {

                public Action handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                    final HttpEntity entity = response.getEntity();
                    final String line = IOUtils.toString(entity.getContent());
                    final JSONObject json = JSONObject.fromObject(line);
                    final JSONArray rows = json.getJSONArray("rows");
                    long seq = 0;
                    for (int i = 0; i < rows.size(); i++) {
                        final JSONObject row = rows.getJSONObject(i);
                        seq = row.getLong("key");
                        changesHandler.onChange(seq, row.getJSONObject("doc"));
                    }
                    changesHandler.onEndOfSequence(seq);
                    /*
                     * If we received the full limit of rows, assume we can
                     * immediately pull more.
                     */
                    return limit == rows.size() ? Action.CONTINUE : Action.PAUSE;
                }
            };

            final HttpGet get = new HttpGet(url + "_all_docs_by_seq?include_docs=true&limit=" + limit + "&startkey=" + since);
            return httpClient.execute(get, responseHandler);
        }
    }

    public static final class V11 extends Database {

        public V11(final HttpClient httpClient, final String url) {
            super(httpClient, url);
        }

        public Action handleChanges(final long since, final ChangesHandler changesHandler) throws IOException {
            final ResponseHandler<Action> responseHandler = new ResponseHandler<Action>() {
                public Action handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                    final HttpEntity entity = response.getEntity();
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final JSONObject json = JSONObject.fromObject(line);
                        if (json.has("error")) {
                            changesHandler.onError(json);
                            return Action.ABORT;
                        }
                        if (json.has("last_seq")) {
                            changesHandler.onEndOfSequence(json.getLong("last_seq"));
                            return Action.CONTINUE;
                        }
                        changesHandler.onChange(json.getLong("seq"), json.getJSONObject("doc"));
                    }
                    return Action.CONTINUE;
                }
            };

            final HttpGet get = new HttpGet(url + "_changes?feed=continuous&timeout=30000&include_docs=true&since=" + since);
            return httpClient.execute(get, responseHandler);
        }
    }

    protected final HttpClient httpClient;

    protected final String url;

    public Database(final HttpClient httpClient, final String url) {
        this.httpClient = httpClient;
        this.url = url.endsWith("/") ? url : url + "/";
    }

    public final boolean create() throws IOException {
        return HttpUtils.put(httpClient, url, null) == 201;
    }

    public final boolean delete() throws IOException {
        return HttpUtils.delete(httpClient, url) == 201;
    }

    public JSONArray getAllDesignDocuments(final String dbname) throws IOException {
        return getDocuments("_design", "_design0").getJSONArray("rows");
    }

    public final JSONObject getDocument(final String id) throws IOException {
        final String response = HttpUtils.get(httpClient, url + "/" + Utils.urlEncode(id));
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
                "%s/_all_docs?startkey=%%22%s%%22&endkey=%%22%s%%22&include_docs=true", url, Utils.urlEncode(startkey), Utils
                        .urlEncode(endkey))));
    }

    public final JSONObject getInfo() throws IOException {
        return JSONObject.fromObject(HttpUtils.get(httpClient, url));
    }

    public final <T> T handleAttachment(final String doc, final String att, final ResponseHandler<T> handler) throws IOException {
        final HttpGet get = new HttpGet(url + "/" + Utils.urlEncode(doc) + "/" + Utils.urlEncode(att));
        return httpClient.execute(get, handler);
    }

    public abstract Action handleChanges(final long since, final ChangesHandler handler) throws IOException;

    public enum Action {
        CONTINUE, ABORT, PAUSE;
    }

    public interface ChangesHandler {

        void onChange(final long seq, final JSONObject doc) throws IOException;

        void onError(final JSONObject error) throws IOException;

        void onEndOfSequence(final long seq) throws IOException;
    }

    public final boolean saveDocument(final String id, final String body) throws IOException {
        return HttpUtils.put(httpClient, url + "/" + id, body) == 201;
    }

}
