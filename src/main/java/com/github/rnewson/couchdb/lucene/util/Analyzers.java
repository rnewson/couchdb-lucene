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

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.AnalyzerWrapper;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.cz.CzechAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
		@Override
		public Analyzer newAnalyzer(final String args) {
			return new WhitespaceAnalyzer();
		}
		
		@Override
		public Analyzer newAnalyzer(final JSONObject args) {
			return new WhitespaceAnalyzer();
		}
	},
	NGRAM {
		@Override
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

	public abstract Analyzer newAnalyzer(final String args) throws JSONException;

	public abstract Analyzer newAnalyzer(final JSONObject args) throws JSONException;

	static Logger logger = Logger.getLogger(Analyzers.class.getName());

	protected static final class NGramAnalyzer extends AnalyzerWrapper {
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

	public static Analyzer fromSpec(final JSONObject json, final String analyzerKey) {
		JSONObject spec = json.optJSONObject(analyzerKey);
		if (spec != null) {
			return getAnalyzer(spec);
		} else {
			return getAnalyzer(json.optString(analyzerKey, Constants.DEFAULT_ANALYZER));
		}
	}

	public static Analyzer fromSpec(final JSONObject json) {
		return fromSpec(json, Constants.ANALYZER);
	}

	/*
	 * called from DatabaseIndexer when handling an http search request
	 */
	public static Analyzer fromSpec(String str) {
		if (str == null) {
			return getAnalyzer(Constants.DEFAULT_ANALYZER);
		} 
		
		str = str.trim();
		
		if (str.startsWith("{")) {
			try {
				return getAnalyzer( new JSONObject(str) );
			
			} catch (Exception ex) {
				return getAnalyzer( Constants.DEFAULT_ANALYZER );
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

	public static Analyzer getAnalyzer(final JSONObject json) {
		String className = json.optString(Constants.CLASS);
		JSONArray params = json.optJSONArray(Constants.PARAMS);

		Analyzer newAnalyzer = null;

		if (className == null || className.isEmpty()) {
			Iterator<?> it = json.keys();
			if (it.hasNext()) {
				String key = (String) it.next();
				String args = json.optString(key);
				
				System.err.println("getAnalyzer builtins for " + key + "  with  " + args);
				
				JSONObject obj = json.optJSONObject(key);
				try {
					if (obj != null) {
						return Analyzers.valueOf(key.toUpperCase()).newAnalyzer(obj);
					} else {
						return Analyzers.valueOf(key.toUpperCase()).newAnalyzer(args);
					}
				} catch (Exception ex) { }
			}

			logger.error("Lucene index: analyzer class name is not defined");
			return null;
		}
		
		System.err.println("getAnalyzer for " + className + "  with  " + params);

		// is the class accessible?
		Class<?> clazz = null;
		try {
			clazz = Class.forName(className);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			logger.error(String.format("Lucene index: analyzer class %s not found. (%s)", className, e.getMessage()));
			return null;
		}

		// Is the class an Analyzer?
		if (!Analyzer.class.isAssignableFrom(clazz)) {
			System.err.println("getAnalyzer NOT ASSIGNABLE");
			logger.error(String.format("Lucene index: analyzer class has to be a subclass of %s", Analyzer.class.getName()));
			return null;
		}

		// Get list of parameters
		List<KeyTypedValue> cParams;
		try {
			cParams = getAllConstructorParameters(params);
		} catch (ParameterException pe) {
			pe.printStackTrace();
			// Unable to parse parameters.
			logger.error(String.format("Unable to get parameters for %s: %s", className, pe.getMessage()), pe);
			cParams = new ArrayList<>();
		}

		// Iterate over all parameters, convert data to two arrays
		// that can be used in the reflection code
		final Class<?> cParamClasses[] = new Class<?>[cParams.size()];
		final Object cParamValues[] = new Object[cParams.size()];
		for (int i = 0; i < cParams.size(); i++) {
			KeyTypedValue ktv = cParams.get(i);
			cParamClasses[i] = ktv.getValueClass();
			cParamValues[i] = ktv.getValue();
		}

		// Create new analyzer
		newAnalyzer = createInstance(clazz, cParamClasses, cParamValues);

		if (newAnalyzer == null) {
			logger.error(String.format("Unable to create analyzer '%s'", className));
		}
		
		return newAnalyzer;
	}

	/**
	 * Create instance of the lucene analyzer with provided arguments
	 *
	 * @param clazz The analyzer class
	 * @param vcParamClasses The parameter classes
	 * @param vcParamValues The parameter values
	 * @return The lucene analyzer
	 */
	private static Analyzer createInstance(Class<?> clazz, Class<?>[] vcParamClasses, Object[] vcParamValues) {

		String className = clazz.getName();
		
		System.err.println("createInstance for className: " + className);

		try {
			final Constructor<?> cstr = clazz.getDeclaredConstructor(vcParamClasses);
			cstr.setAccessible(true);
			
			System.err.println("createInstance with constructor: " + cstr);

			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Using analyzer %s", className));
			}

			return (Analyzer) cstr.newInstance(vcParamValues);

		} catch (IllegalArgumentException | IllegalAccessException | InstantiationException | InvocationTargetException | SecurityException e) {
			e.printStackTrace();
			logger.error(String.format("Exception while instantiating analyzer class %s: %s", className, e.getMessage()), e);
		} catch (NoSuchMethodException ex) {
			ex.printStackTrace();
			logger.error(String.format("Could not find matching analyzer class constructor%s: %s", className, ex.getMessage()), ex);
		}

		return null;
	}

	/**
	 * Retrieve parameter info from all <param/> elements.
	 *
	 * @param config The <analyzer/> element from the provided configuration
	 * @return List of triples key-value-valueType
	 * @throws org.exist.indexing.lucene.AnalyzerConfig.ParameterException
	 */
	private static List<KeyTypedValue> getAllConstructorParameters(JSONArray params) throws ParameterException {
		final List<KeyTypedValue> parameters = new ArrayList<>();

		if (params != null) {
			for (int i = 0; i < params.length(); i++) {
				parameters.add(getConstructorParameter(params.getJSONObject(i)));
			}
		}

		return parameters;
	}

	/**
	 * Retrieve configuration information from one json param object. Type
	 * information is used to construct actual data containing objects.
	 * 
	 * Each param object looks like:
	 * 
	 * <pre>{ "name": &lt;a name>, "type": &lt;oneof: set, bool, int, file, string>, "value": &lt;value> }</pre>
	 * 
	 * The name is not used. Values of type <code>set</code> are JSON arrays and 
	 * are used to represent lucene CharArraySets such as for stop words in StandardAnalyzer
	 *
	 * @param param Element that represents <param/>
	 * @return Triple key-value-value-type
	 * @throws org.exist.indexing.lucene.AnalyzerConfig.ParameterException
	 */
	private static KeyTypedValue getConstructorParameter(JSONObject param) throws ParameterException {

		final String name = param.optString("name");
		final String type = param.optString("type", "string");
		final String value = param.optString("value");

		KeyTypedValue parameter = null;

		switch (type) {

		case "string": {
			if (value == null) {
				throw new ParameterException("The 'value' field of a string param must exist and must contain a String value.");
			}

			parameter = new KeyTypedValue(name, value, String.class);

			break;
		}

		// "java.io.FileReader":
		case "file": {

			if (value == null) {
				throw new ParameterException("The 'value' field of a file param must exist and must contain a file name.");
			}

			try {
				// ToDo: check where to close reader to prevent resource leakage
				Reader fileReader = new java.io.FileReader(value);
				parameter = new KeyTypedValue(name, fileReader, Reader.class);

			} catch (java.io.FileNotFoundException ex) {
				logger.error(String.format("File '%s' could not be found.", value), ex);
			}
			break;
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

			parameter = new KeyTypedValue(name, CharArraySet.copy(set), CharArraySet.class);
			break;
		}

		// "java.lang.Integer":
		case "int":

			int n = param.optInt("value");
			parameter = new KeyTypedValue(name, n, int.class);

			break;

		// "java.lang.Boolean":
		case "boolean":

			boolean b = param.optBoolean("value");
			parameter = new KeyTypedValue(name, b, boolean.class);
			break;

		default:
			// there was no match
			logger.error(String.format("Unknown lucene analyzer parameter type: %s", type));
			break;
		}

		return parameter;
	}

	/**
	 * CLass for containing the Triple : key (name), corresponding value and
	 * class type of value.
	 */
	private static class KeyTypedValue {

		private final String key;
		private final Object value;
		private final Class<?> valueClass;

		public KeyTypedValue(String key, Object value) {
			this(key, value, value.getClass());
		}

		public KeyTypedValue(String key, Object value, Class<?> valueClass) {
			this.key = key;
			this.value = value;
			this.valueClass = valueClass;
		}

		@SuppressWarnings("unused")
		public String getKey() {
			return key;
		}

		public Object getValue() {
			return value;
		}

		public Class<?> getValueClass() {
			return valueClass;
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
}
