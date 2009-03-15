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

	private final Function userFun;

	private final Function systemFun;

	private final String fun;

	public Rhino(final String fun) throws IOException {
		this.fun = fun;
		this.context = contextFactory.enterContext();
		context.setOptimizationLevel(9);
		scope = context.initStandardObjects();

		// compile user-defined function.
		this.userFun = context.compileFunction(scope, fun, "userFun", 0, null);

		// compile system function.
		this.systemFun = context.compileFunction(scope,
				"function(json,filter) { var doc=JSON.parse(json); doc=filter(doc); return JSON.stringify(doc); }",
				"systemFun", 0, null);

		// add JSON parser.
		context.evaluateString(scope, loadJSONParser(), "json2", 0, null);
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
		return (String) systemFun.call(context, scope, null, new Object[] { doc, userFun });
	}

	public String getSignature() {
		return Utils.digest(fun);
	}

	public void close() {
		Context.exit();
	}

	public String toString() {
		return fun;
	}

}
