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
		final Scriptable o = Context.toObject(this, scope);
		System.err.println(o);
		scope.put("out", scope, o);

		final String script = String.format("function(json) { var fun=%s; return fun(eval('('+json+')')); }", fun);

		this.function = context.compileFunction(scope, script, "", 0, null);

	}

	public void emit_text(final Object key, final String val) {
		System.err.printf("%s: %s\n", key, val);
	}

	public void emit_int(final Object key, final Double val) {
		System.err.printf("%s: %s\n", key, val);
	}

	public void emit_date(final Object key, final String val) {
		System.err.printf("%s: %s\n", key, val.getClass());
	}

	public String parse(final String doc) {
		return function.call(context, scope, null, new Object[] { doc }).toString();
	}

	public void close() {
		Context.exit();
	}

}
