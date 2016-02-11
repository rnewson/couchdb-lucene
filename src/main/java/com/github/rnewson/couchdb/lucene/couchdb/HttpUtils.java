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

import com.github.rnewson.couchdb.lucene.util.Constants;
import com.github.rnewson.couchdb.lucene.util.ErrorPreservingResponseHandler;
import com.github.rnewson.couchdb.lucene.util.StatusCodeResponseHandler;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.json.JSONObject;

import java.io.IOException;

public final class HttpUtils {

    private HttpUtils() {
        throw new InstantiationError("This class is not supposed to be instantiated.");
    }
    public static final int delete(final HttpClient httpClient, final String url) throws IOException {
        return httpClient.execute(new HttpDelete(url), new StatusCodeResponseHandler());
    }

    public static final String execute(final HttpClient httpClient, final HttpUriRequest request) throws IOException {
        return httpClient.execute(request, new ErrorPreservingResponseHandler());
    }

    public static final String get(final HttpClient httpClient, final String url) throws IOException {
        return execute(httpClient, new HttpGet(url));
    }

    public static final String post(final HttpClient httpClient, final String url, final JSONObject body) throws IOException {
        final HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", Constants.APPLICATION_JSON);
        post.setEntity(new StringEntity(body.toString(), "UTF-8"));
        return execute(httpClient, post);
    }

    public static final int put(final HttpClient httpClient, final String url, final String body) throws IOException {
        final HttpPut put = new HttpPut(url);
        if (body != null) {
            put.setHeader("Content-Type", Constants.APPLICATION_JSON);
            put.setEntity(new StringEntity(body, "UTF-8"));
        }
        return httpClient.execute(put, new StatusCodeResponseHandler());
    }

}
