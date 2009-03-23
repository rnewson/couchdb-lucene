package com.github.rnewson.couchdb.lucene;

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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.cz.CzechAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.el.GreekAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;

class Utils {

	private static final Map<String, Analyzer> ANALYZERS = new HashMap<String, Analyzer>();

	static {
		ANALYZERS.put("de", new GermanAnalyzer());
		ANALYZERS.put("fr", new FrenchAnalyzer());
		ANALYZERS.put("el", new GreekAnalyzer());
		ANALYZERS.put("nl", new DutchAnalyzer());
		ANALYZERS.put("ru", new RussianAnalyzer());
		ANALYZERS.put("br", new BrazilianAnalyzer());
		ANALYZERS.put("cz", new CzechAnalyzer());
		ANALYZERS.put("th", new ThaiAnalyzer());
		ANALYZERS.put("en", new StandardAnalyzer());

		final Analyzer cjk = new CJKAnalyzer();
		ANALYZERS.put("zh", cjk);
		ANALYZERS.put("ja", cjk);
		ANALYZERS.put("ko", cjk);
	}

	public static final Analyzer DEFAULT_ANALYZER = new StandardAnalyzer();

	public static synchronized Analyzer getAnalyzer(final String language) {
		final Analyzer result = ANALYZERS.get(language);
		return result != null ? result : DEFAULT_ANALYZER;
	}

	public static void log(final String fmt, final Object... args) {
		final String msg = String.format(fmt, args);
		System.out.printf("{\"log\":\"%s\"}\n", msg);
	}

	public static String throwableToJSON(final Throwable t) {
		return error(t.getMessage() == null ? "Unknown error" : String.format("%s: %s", t.getClass(), t.getMessage()));
	}

	public static String error(final String txt) {
		return error(500, txt);
	}

	public static String digest(final String data) {
		return DigestUtils.md5Hex(data);
	}

	public static String error(final int code, final Throwable t) {
		final StringWriter writer = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(writer);
		if (t.getMessage() != null)
			printWriter.append(t.getMessage());
		t.printStackTrace(printWriter);
		return new JSONObject().element("code", code).element("body", writer.toString()).toString();
	}

	public static String error(final int code, final String txt) {
		return new JSONObject().element("code", code).element("body", StringEscapeUtils.escapeHtml(txt)).toString();
	}

	public static Field text(final String name, final String value, final boolean store) {
		return new Field(name, value, store ? Store.YES : Store.NO, Field.Index.ANALYZED);
	}

	public static Field token(final String name, final String value, final boolean store) {
		return new Field(name, value, store ? Store.YES : Store.NO, Field.Index.NOT_ANALYZED_NO_NORMS);
	}

}
