package org.apache.couchdb.lucene;

import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

public class RhinoTest {

	@Test
	public void testSimpleEval() {
		final String source = "function() { var doc = {\"size\":12}; return doc.size; } ";

		final Context ctx = new ContextFactory().enterContext();
		ctx.setLanguageVersion(170);
		final Scriptable scope = ctx.initStandardObjects();

		final Function function = ctx.compileFunction(scope, source, "fun", 0, null);

		Object obj = function.call(ctx, scope, null, null);
		System.err.println(obj);
	}

}
