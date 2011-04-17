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

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Document {

	public static Document deletedDocument(final String id) {
		final Document result = new Document();
		result.setId(id);
		result.setDeleted(true);
		return result;
	}

	private boolean deleted;

	private String id;

	public String getId() {
		return id;
	}

	public boolean isDeleted() {
		return deleted;
	}

	@JsonProperty("_deleted")
	public void setDeleted(final boolean deleted) {
		this.deleted = deleted;
	}

	@JsonProperty("_id")
	public void setId(final String id) {
		this.id = id;
	}

}
