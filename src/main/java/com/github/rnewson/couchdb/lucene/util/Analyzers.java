package com.github.rnewson.couchdb.lucene.util;

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

import java.util.Iterator;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class Analyzers {

    private static final ApplicationContext context = new ClassPathXmlApplicationContext(
            "analyzers.xml");

    public static Analyzer getAnalyzer(final String str) throws JSONException {
        final String[] parts = str.split(":", 2);
        final String name = parts[0].toUpperCase();

        if ("PERFIELD".equals(name)) {
            final String args = parts.length == 2 ? parts[1] : null;
            return perfield(args);
        }
        return context.getBean(name, Analyzer.class);
    }

    private static Analyzer perfield(final String args) throws JSONException {
        final JSONObject json = new JSONObject(args == null ? "{}" : args);
        final Analyzer defaultAnalyzer = Analyzers.getAnalyzer(json.optString(
                Constants.DEFAULT_FIELD,
                "standard"));
        final PerFieldAnalyzerWrapper result = new PerFieldAnalyzerWrapper(defaultAnalyzer);
        final Iterator<?> it = json.keys();
        while (it.hasNext()) {
            final String key = it.next().toString();
            if (Constants.DEFAULT_FIELD.equals(key))
                continue;
            result.addAnalyzer(key, Analyzers.getAnalyzer(json.getString(key)));
        }
        return result;
    }

}
