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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.http.client.HttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mortbay.jetty.HttpHeaders;

/**
 * Simple Java API access to CouchDB.
 * 
 * @author rnewson
 * 
 */
public class Couch {

	private final HttpClient httpClient;
	private final String url;
	private final String sessionTokenId = "AuthSession";
	private final Map<String, String> headers;
	private final Pattern authSessionCookiePattern = Pattern.compile(".*" + sessionTokenId + "=");
	public Couch(final HttpClient httpClient, final String url, HttpServletRequest request)
			throws IOException {
		this.httpClient = httpClient;
		this.url = url.endsWith("/") ? url : url + "/";
		headers = new HashMap<String, String>();
		final String sessionId = decodeAuthToken(request.getHeader(HttpHeaders.COOKIE));
		if (sessionId != null) { //We found a session, use it
		    headers.put("Cookie", sessionTokenId+'='+sessionId);
		    headers.put("X-CouchDB-WWW-Authenticate", "Cookie");
		}  else if (request.getHeader("Authorization") != null) { //Maybe basic auth is used then?
		    headers.put("Authorization", request.getHeader("Authorization"));
		}
	}

	private  String decodeAuthToken(String cookie) {
        if (cookie == null) {
            return null;
        }
        final String tokens[] = authSessionCookiePattern.split(cookie, 2);
        if (tokens.length > 1) {
            final String cookiePart = tokens[1];
            final int splitIndex = cookiePart.indexOf(";");
            if (splitIndex >= 0) {
                return (cookiePart.substring(0, splitIndex));
            } else {
                return cookiePart;
            }
        }
        return null;
    }
	public final JSONArray getAllDatabases() throws IOException, JSONException {
		final String response = HttpUtils.get(httpClient, url + "_all_dbs", headers);
		return new JSONArray(response);
	}

	public final JSONObject getInfo() throws IOException, JSONException {
		return new JSONObject(HttpUtils.get(httpClient, url, headers));
	}

	public Database getDatabase(final String dbname) throws IOException {
	    return new Database(httpClient, url + dbname, headers);
	}
}
