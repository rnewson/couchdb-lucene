package org.apache.couchdb.lucene;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

public final class Rhino {

	private final ContextFactory contextFactory = new ContextFactory();

	private final Context context;

	private final Scriptable scope;

	private final Function function;

	public Rhino(final String fun) {
		this.context = contextFactory.enterContext();
		scope = context.initStandardObjects();

		final String script = String.format("function(json) { var fun=%s; return fun(eval('('+json+')')); }", fun);

		this.function = context.compileFunction(scope, script, "", 0, null);
	}

	public String parse(final String doc) {
		return function.call(context, scope, null, new Object[] { doc }).toString();
	}

	public void close() {
		Context.exit();
	}

}
