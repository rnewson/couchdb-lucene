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
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;

import com.github.rnewson.couchdb.lucene.Lucene;
import com.github.rnewson.couchdb.lucene.util.Analyzers;

public final class View {

    private static final String DEFAULT_ANALYZER = "standard";

    private static final String ANALYZER = "analyzer";

    private static final String INDEX = "index";

    private static final String DEFAULTS = "defaults";

    private final JSONObject json;

    public View(final JSONObject json) {
        if (!json.has(INDEX)) {
            throw new IllegalArgumentException(json + " is not an index");
        }
        this.json = json;
    }

    public Analyzer getAnalyzer() {
        return Analyzers.getAnalyzer(json.optString(ANALYZER, DEFAULT_ANALYZER));
    }

    public ViewSettings getDefaultSettings() {
        return json.has(DEFAULTS) ? new ViewSettings(json.getJSONObject(DEFAULTS)) : ViewSettings
                .getDefaultSettings();
    }

    public String getFunction() {
        return trim(json.getString(INDEX));
    }

    public Function compileFunction(final Context context, ScriptableObject scope) {
        return context.compileFunction(scope, getFunction(), null, 0, null);
    }

    public String getDigest() {
        return Lucene.digest(json);
    }

    private String trim(final String fun) {
        String result = fun;
        result = StringUtils.trim(result);
        result = StringUtils.removeStart(result, "\"");
        result = StringUtils.removeEnd(result, "\"");
        return result;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((json == null) ? 0 : json.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof View)) {
            return false;
        }
        View other = (View) obj;
        return getDigest().equals(other.getDigest());
    }

}
