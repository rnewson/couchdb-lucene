package com.github.rnewson.couchdb.lucene.couchdb;

/**
 * Copyright 2010 Robert Newson
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.sf.json.JSONObject;

public class DesignDocument extends CouchDocument {

	private final JSONObject fulltext;

	public DesignDocument(final JSONObject json) {
		super(json);
		if (!getId().startsWith("_design/")) {
			throw new IllegalArgumentException(json
					+ " is not a design document");
		}
		fulltext = json.optJSONObject("fulltext");
	}

	public DesignDocument(final CouchDocument doc) {
		this(doc.json);
	}

	public View getView(final String name) {
		if (fulltext == null)
			return null;
		final JSONObject json = fulltext.optJSONObject(name);
		return json == null ? null : new View(json);
	}

	public Map<String, View> getAllViews() {
		if (fulltext == null)
			return Collections.emptyMap();
		final Map<String, View> result = new HashMap<String, View>();
		for (final Object key : fulltext.keySet()) {
			final String name = (String) key;
			final View view = getView(name);
			if (view != null) {
				result.put(name, view);
			}
		}
		return result;
	}

}
