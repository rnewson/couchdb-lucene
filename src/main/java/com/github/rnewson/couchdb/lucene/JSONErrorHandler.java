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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.handler.ErrorHandler;

import com.github.rnewson.couchdb.lucene.util.ServletUtils;

/**
 * Convert errors to CouchDB-style JSON objects.
 * 
 * @author rnewson
 * 
 */
public final class JSONErrorHandler extends ErrorHandler {

    public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException {
        HttpConnection connection = HttpConnection.getCurrentConnection();
        connection.getRequest().setHandled(true);
        final String reason = connection.getResponse().getReason();
        try {
            if (reason != null && reason.startsWith("{")) {
                ServletUtils.sendJsonError(request, response, connection.getResponse().getStatus(),
                        new JSONObject(reason));
            } else {
                ServletUtils.sendJsonError(request, response, connection.getResponse().getStatus(),
                        reason);
            }
        } catch (final JSONException e) {
            response.sendError(500);
        }
    }

}
