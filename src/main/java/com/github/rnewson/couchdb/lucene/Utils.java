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

import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

class Utils {

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

    public static Query docQuery(final String dbname, final String id) {
        BooleanQuery q = new BooleanQuery();
        q.add(new TermQuery(new Term(Config.DB, dbname)), Occur.MUST);
        q.add(new TermQuery(new Term(Config.ID, id)), Occur.MUST);
        return q;
    }
}
