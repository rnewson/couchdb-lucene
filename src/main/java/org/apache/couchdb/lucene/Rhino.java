package org.apache.couchdb.lucene;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;

public final class Rhino {

	private final ContextFactory contextFactory = new ContextFactory();

	private final Context context;

	private final Scriptable scope;

	private final Function function;

	private final String fun;

	public Rhino(final String fun) {
		this.fun = fun;
		this.context = contextFactory.enterContext();
		scope = context.initStandardObjects();
		final String script = String.format("function(json) { var fun=%s; return fun(eval('('+json+')')); }", fun);
		this.function = context.compileFunction(scope, script, "", 0, null);
	}

	/**
	 * TODO return JSON string.
	 */
	public NativeObject parse(final String doc) {
		return (NativeObject) function.call(context, scope, null, new Object[] { doc });
	}

	public void close() {
		Context.exit();
	}

	public String toString() {
		return fun;
	}

}
