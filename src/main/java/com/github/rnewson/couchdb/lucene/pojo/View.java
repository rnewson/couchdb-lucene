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

@JsonIgnoreProperties(ignoreUnknown = true)
public final class View {

	private String analyzer = "standard";

	private ViewSettings defaults = new ViewSettings();

	private String index;

	public String getAnalyzer() {
		return analyzer;
	}

	public ViewSettings getDefaults() {
		return defaults;
	}

	public String getIndex() {
		return index;
	}

	public void setAnalyzer(final String analyzer) {
		this.analyzer = analyzer;
	}

	public void setDefaults(final ViewSettings defaults) {
		this.defaults = defaults;
	}

	public void setIndex(final String index) {
		this.index = index;
	}

}
