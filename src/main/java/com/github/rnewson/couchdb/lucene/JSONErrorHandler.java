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

package com.github.rnewson.couchdb.lucene;

import com.github.rnewson.couchdb.lucene.util.ServletUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.json.JSONException;
import org.json.JSONObject;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Convert errors to CouchDB-style JSON objects.
 *
 * @author rnewson
 */
public final class JSONErrorHandler extends ErrorHandler {

    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        final String reason = baseRequest.getResponse().getReason();
        try {
            if (reason != null && reason.startsWith("{")) {
                ServletUtils.sendJsonError(request, response, baseRequest.getResponse().getStatus(),
                        new JSONObject(reason));
            } else {
                ServletUtils.sendJsonError(request, response, baseRequest.getResponse().getStatus(),
                        reason);
            }
        } catch (final JSONException e) {
            response.sendError(500);
        }

    }

}
