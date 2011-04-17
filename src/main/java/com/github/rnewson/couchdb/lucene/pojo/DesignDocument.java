package com.github.rnewson.couchdb.lucene.pojo;

/**

 * Copyright 2011 Robert Newson
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
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;

public final class DesignDocument extends Document {

	private Map<String, View> fulltext = Collections.emptyMap();

	public Map<String, View> getFulltext() {
		return fulltext;
	}

	public View getFulltext(final String name) {
		return fulltext.get(name);
	}

	@JsonProperty("fulltext")
	public void setFulltext(final Map<String, View> fulltext) {
		this.fulltext = fulltext;
	}

}
