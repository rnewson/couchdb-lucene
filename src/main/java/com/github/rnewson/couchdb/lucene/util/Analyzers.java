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

package com.github.rnewson.couchdb.lucene.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.AnalyzerWrapper;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.LowerCaseTokenizer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.cz.CzechAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum Analyzers {

    BRAZILIAN {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new BrazilianAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new BrazilianAnalyzer();
        }
    },
    CHINESE {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new SmartChineseAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new SmartChineseAnalyzer();
        }
    },
    CJK {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new CJKAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new CJKAnalyzer();
        }
    },
    CLASSIC {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new ClassicAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new ClassicAnalyzer();
        }
    },
    CZECH {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new CzechAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new CzechAnalyzer();
        }
    },
    DUTCH {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new DutchAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new DutchAnalyzer();
        }
    },
    ENGLISH {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new StandardAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new StandardAnalyzer();
        }
    },
    FRENCH {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new FrenchAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new FrenchAnalyzer();
        }
    },
    GERMAN {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new GermanAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new GermanAnalyzer();
        }
    },
    KEYWORD {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new KeywordAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new KeywordAnalyzer();
        }
    },
    PERFIELD {
        @Override
        public Analyzer newAnalyzer(final String args) throws JSONException {
            final JSONObject json = new JSONObject(args == null ? "{}" : args);
            return PERFIELD.newAnalyzer(json);
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject json) throws JSONException {
            final Analyzer defaultAnalyzer = fromSpec(json, Constants.DEFAULT_FIELD);
            final Map<String, Analyzer> analyzers = new HashMap<>();
            final Iterator<?> it = json.keys();
            while (it.hasNext()) {
                final String key = it.next().toString();
                if (Constants.DEFAULT_FIELD.equals(key))
                    continue;
                analyzers.put(key, fromSpec(json, key));
            }
            return new PerFieldAnalyzerWrapper(defaultAnalyzer, analyzers);
        }
    },
    RUSSIAN {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new RussianAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new RussianAnalyzer();
        }
    },
    SIMPLE {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new SimpleAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new SimpleAnalyzer();
        }
    },
    STANDARD {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new StandardAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new StandardAnalyzer();
        }
    },
    THAI {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new ThaiAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new ThaiAnalyzer();
        }
    },
    WHITESPACE {
        public Analyzer newAnalyzer(final String args) {
            return new WhitespaceAnalyzer();
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject args) {
            return new WhitespaceAnalyzer();
        }
    },
    NGRAM {
        public Analyzer newAnalyzer(final String args) throws JSONException {
            final JSONObject json = new JSONObject(args == null ? "{}" : args);
            return NGRAM.newAnalyzer(json);
        }
        @Override
        public Analyzer newAnalyzer(final JSONObject json) throws JSONException {
            Analyzer analyzer = fromSpec(json);
            int min = json.optInt("min", NGramTokenFilter.DEFAULT_MIN_NGRAM_SIZE);
            int max = json.optInt("max", NGramTokenFilter.DEFAULT_MAX_NGRAM_SIZE);
            return new NGramAnalyzer(analyzer, min, max);
        }
    };

    private static final class NGramAnalyzer extends AnalyzerWrapper {
        private final Analyzer analyzer;
        private final int min;
        private final int max;

        public NGramAnalyzer(final Analyzer analyzer, final int min, final int max) {
            super(Analyzer.GLOBAL_REUSE_STRATEGY);
            this.analyzer = analyzer;
            this.min = min;
            this.max = max;
        }

        @Override
        protected Analyzer getWrappedAnalyzer(final String fieldName) {
            return analyzer;
        }

        @Override
        protected TokenStreamComponents wrapComponents(String fieldName, TokenStreamComponents components) {
            return new TokenStreamComponents(components.getTokenizer(),
                new NGramTokenFilter(components.getTokenStream(),
                    this.min, this.max));
        }
    }

    public static Analyzer fromSpec(final JSONObject json, final String analyzerKey) throws JSONException {
        JSONObject spec = json.optJSONObject(analyzerKey);
        if (spec != null) {
            return getAnalyzer(spec);
        } else {
            return getAnalyzer(json.optString(analyzerKey, Constants.DEFAULT_ANALYZER));
        }
    }

    public static Analyzer fromSpec(final JSONObject json) throws JSONException {
        return fromSpec(json, Constants.ANALYZER);
    }

    /*
     * called from DatabaseIndexer when handling an http search request
     */
    public static Analyzer fromSpec(String str) throws JSONException {
        if (str == null) {
            return getAnalyzer(Constants.DEFAULT_ANALYZER);
        } 

        if (str.startsWith("{")) {
            try {
                return getAnalyzer(new JSONObject(str));

            } catch (JSONException ex) {
                logger.error("Analyzer spec is not well-formed json. Using default analyzer!", ex);
                return getAnalyzer(Constants.DEFAULT_ANALYZER);
            }
        }

        return getAnalyzer(str);
    }

    public static Analyzer getAnalyzer(final String str) throws JSONException {
        final String[] parts = str.split(":", 2);
        final String name = parts[0].toUpperCase();
        final String args = parts.length == 2 ? parts[1] : null;
        return Analyzers.valueOf(name).newAnalyzer(args);
    }

    public static Analyzer getAnalyzer(final JSONObject json) throws JSONException {
        String className = json.optString(Constants.CLASS);
        JSONArray params = json.optJSONArray(Constants.PARAMS);

        if (className == null || className.isEmpty()) {
            Iterator<?> it = json.keys();
            if (it.hasNext()) {
                String key = (String) it.next();
                String args = json.optString(key);

                JSONObject obj = json.optJSONObject(key);
                if (obj != null) {
                    return Analyzers.valueOf(key.toUpperCase()).newAnalyzer(obj);
                } else {
                    return Analyzers.valueOf(key.toUpperCase()).newAnalyzer(args);
                }
            }

            logger.error("No analyzer class name defined in " + json);
            return null;
        }

        // is the class accessible?
        Class<?> clazz = null;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            logger.error("Analyzer class " + className + " not found. " + e.getMessage(), e);
            return null;
        }

        // Is the class an Analyzer?
        if (!Analyzer.class.isAssignableFrom(clazz)) {
            logger.error(clazz.getName() + " has to be a subclass of " + Analyzer.class.getName());
            return null;
        }

        // Get list of parameters
        List<ParamSpec> paramSpecs;
        try {
            paramSpecs = getParamSpecs(params);
        } catch (ParameterException | JSONException ex) {
            logger.error("Unable to parse parameter specs for " + className + ". " + ex.getMessage(), ex);
            return null;
        }

        // split param specs into classes and values for constructor lookup
        final Class<?> paramClasses[] = new Class<?>[paramSpecs.size()];
        final Object paramValues[] = new Object[paramSpecs.size()];
        for (int i = 0; i < paramSpecs.size(); i++) {
            ParamSpec spec = paramSpecs.get(i);
            paramClasses[i] = spec.getValueClass();
            paramValues[i] = spec.getValue();
        }

        // Create new analyzer
        return newAnalyzer(clazz, paramClasses, paramValues);
    }

    /**
     * Create instance of the lucene analyzer with provided arguments
     *
     * @param clazz The analyzer class
     * @param paramClasses The parameter classes
     * @param paramValues The parameter values
     * @return The lucene analyzer
     */
    private static Analyzer newAnalyzer(Class<?> clazz, Class<?>[] paramClasses, Object[] paramValues) {

        String className = clazz.getName();

        try {
            final Constructor<?> cstr = clazz.getDeclaredConstructor(paramClasses);

            return (Analyzer) cstr.newInstance(paramValues);

        } catch (IllegalArgumentException | IllegalAccessException | InstantiationException | InvocationTargetException | SecurityException e) {
            logger.error("Exception while instantiating analyzer class " + className + ". " + e.getMessage(), e);
        } catch (NoSuchMethodException ex) {
            logger.error("Could not find matching analyzer class constructor for " + className + " " + ex.getMessage(), ex);
        }

        return null;
    }

    /**
     * Retrieve the list of parameter specs for the analyzer
     */
    private static List<ParamSpec> getParamSpecs(JSONArray jsonParams) throws ParameterException, JSONException {
        final List<ParamSpec> paramSpecs = new ArrayList<>();

        if (jsonParams != null) {
            for (int i = 0; i < jsonParams.length(); i++) {
                paramSpecs.add(getParamSpec(jsonParams.getJSONObject(i)));
            }
        }

        return paramSpecs;
    }

    /**
     * Parse an analyzer constructor parameter spec. 
     * 
     * Each param spec looks like:
     * 
     * <pre>{ "name": &lt;a name>, "type": &lt;oneof: set, bool, int, file, string>, "value": &lt;value> }</pre>
     * 
     * The name serves to document the purpose of the parameter. Values of type <code>set</code> are JSON arrays and 
     * are used to represent lucene CharArraySets such as for stop words in StandardAnalyzer
     *
     * @param param json object specifying an analyzer parameter
     * @return ParamSpec
     * @throws ParameterException
     * @throws JSONException
     */
    private static ParamSpec getParamSpec(JSONObject param) throws ParameterException, JSONException {

        final String name = param.optString("name");
        final String type = param.optString("type", "string");
        final String value = param.optString("value");

        switch (type) {

        // String
        case "string": {
            if (value == null) {
                throw new ParameterException("Value for string param: " + name + " is not empty!");
            }

            return new ParamSpec(name, value, String.class);
        }
        // "java.io.FileReader":
        case "file": {

            if (value == null) {
                throw new ParameterException("The 'value' field of a file param must exist and must contain a file name.");
            }

            try {
                // The analyzer is responsible for closing the file
                Reader fileReader = new java.io.FileReader(value);
                return new ParamSpec(name, fileReader, Reader.class);

            } catch (java.io.FileNotFoundException ex) {
                throw new ParameterException("File " + value + " for param " + name + " not found!");
            }
        }
        // "org.apache.lucene.analysis.util.CharArraySet":
        case "set": {
            JSONArray values = param.optJSONArray("value");

            if (values == null) {
                throw new ParameterException("The 'value' field of a set param must exist and must contain a json array of strings.");
            }

            final Set<String> set = new HashSet<>();

            for (int i = 0; i < values.length(); i++) {
                set.add(values.getString(i));
            }

            return new ParamSpec(name, CharArraySet.copy(set), CharArraySet.class);
        }
        // "int":
        case "int":

            int n = param.optInt("value");
            return new ParamSpec(name, n, int.class);

        // "boolean":
        case "boolean":

            boolean b = param.optBoolean("value");
            return new ParamSpec(name, b, boolean.class);
        
        default:
            // there was no match
            logger.error("Unknown parameter type: " + type + " for param: " + name + " with value: " + value);
            break;
        }

        return null;
    }

    /**
     * CLass for containing the Triple : key (name), corresponding value and
     * class type of value.
     */
    private static final class ParamSpec {

        private final String key;
        private final Object value;
        private final Class<?> clazz;

        @SuppressWarnings("unused")
        public ParamSpec(String key, Object value) {
            this(key, value, value.getClass());
        }

        public ParamSpec(String key, Object value, Class<?> clazz) {
            this.key = key;
            this.value = value;
            this.clazz = clazz;
        }

        @SuppressWarnings("unused")
        public String getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

        public Class<?> getValueClass() {
            return clazz;
        }
    }

    /**
     * Exception class to for reporting problems with the parameters.
     */
    @SuppressWarnings("serial")
    private static class ParameterException extends Exception {

        public ParameterException(String message) {
            super(message);
        }
    }

    public abstract Analyzer newAnalyzer(final String args) throws JSONException;

    public abstract Analyzer newAnalyzer(final JSONObject args) throws JSONException;

    static Logger logger = LoggerFactory.getLogger(Analyzers.class.getName());

}
