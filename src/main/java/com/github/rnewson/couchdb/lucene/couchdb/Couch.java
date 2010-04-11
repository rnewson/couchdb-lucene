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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.http.client.HttpClient;

/**
 * Simple Java API access to CouchDB.
 * 
 * @author rnewson
 * 
 */
public class Couch {

	private final HttpClient httpClient;
	private final String url;

	public Couch(final HttpClient httpClient, final String url)
			throws IOException {
		this.httpClient = httpClient;
		this.url = url.endsWith("/") ? url : url + "/";
	}

	public final String[] getAllDatabases() throws IOException {
		final String response = HttpUtils.get(httpClient, url + "_all_dbs");
		final JSONArray arr = JSONArray.fromObject(response);
		return (String[]) arr.toArray(new String[0]);
	}

	public final JSONObject getInfo() throws IOException {
		return JSONObject.fromObject(HttpUtils.get(httpClient, url));
	}

	public Database getDatabase(final String dbname) throws IOException {
		return new Database(httpClient, url + dbname);
	}

}
