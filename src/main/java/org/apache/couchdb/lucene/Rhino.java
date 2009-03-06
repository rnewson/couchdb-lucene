package org.apache.couchdb.lucene;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

public final class Rhino {

	private final ContextFactory contextFactory = new ContextFactory();

	private final Context context;

	private final Scriptable scope;

	private final Function function;

	private final String fun;

	public Rhino(final String fun) throws IOException {
		this.fun = fun;
		this.context = contextFactory.enterContext();
		context.setOptimizationLevel(9);
		scope = context.initStandardObjects();

		final String json2Script = loadJSONParser();

		// evaluate JSON parser/stringifier.
		context.evaluateString(scope, json2Script, "json2", 0, null);

		// compile user-defined javascript function.
		final String f = String.format("function(json) { var fun=%s; var obj=JSON.parse(json); "
				+ "var result=fun(obj); return JSON.stringify(result); }", fun);
		this.function = context.compileFunction(scope, f, "fn", 0, null);
	}

	private String loadJSONParser() throws IOException {
		final InputStream in = Rhino.class.getClassLoader().getResourceAsStream("json2.js");
		try {
			return IOUtils.toString(in, "UTF-8");
		} finally {
			in.close();
		}
	}

	public String parse(final String doc) {
		return (String) function.call(context, scope, null, new Object[] { doc });
	}

	public void close() {
		Context.exit();
	}

	public String toString() {
		return fun;
	}

}
