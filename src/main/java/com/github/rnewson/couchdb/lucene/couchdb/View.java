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

import com.github.rnewson.couchdb.lucene.util.Analyzers;
import com.github.rnewson.couchdb.lucene.util.Constants;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class View {

    private final JSONObject json;

    private final String name;

    public View(final String name, final JSONObject json) {
        if (!json.has(Constants.INDEX)) {
            throw new IllegalArgumentException(json + " is not an index");
        }
        this.name = name;
        this.json = json;
    }

    public Analyzer getAnalyzer() throws JSONException {
        return Analyzers.fromSpec(json);
    }

    public ViewSettings getDefaultSettings() throws JSONException {
        return json.has(Constants.DEFAULTS) ? new ViewSettings(json
                .getJSONObject(Constants.DEFAULTS)) : ViewSettings.getDefaultSettings();
    }

    public String getFunction() throws JSONException {
        return trim(json.getString(Constants.INDEX));
    }

    public Function compileFunction(final Context context,
                                    ScriptableObject scope) throws JSONException {
        return context.compileFunction(scope, getFunction(), null, 0, null);
    }

    public String getDigest() {
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(toBytes(json.optString("analyzer")));
            md.update(toBytes(json.optString("defaults")));
            md.update(toBytes(json.optString("index")));
            return new BigInteger(1, md.digest()).toString(Character.MAX_RADIX);
        } catch (final NoSuchAlgorithmException e) {
            throw new Error("MD5 support missing.");
        }
    }

    private static byte[] toBytes(final String str) {
        if (str == null) {
            return new byte[0];
        }
        try {
            return str.getBytes("UTF-8");
        } catch (final UnsupportedEncodingException e) {
            throw new Error("UTF-8 support missing.");
        }
    }

    private static String trim(final String fun) {
        String result = fun;
        result = StringUtils.trim(result);
        result = StringUtils.removeStart(result, "\"");
        result = StringUtils.removeEnd(result, "\"");
        return result;
    }

    @Override
    public int hashCode() {
        return getDigest().hashCode();
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

    @Override
    public String toString() {
        return String.format("View[name=%s, digest=%s]", name, getDigest());
    }

}
