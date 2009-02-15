package org.apache.couchdb.lucene;

import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

public class RhinoTest {

	@Test
	public void testSimpleEval() {
		final String source = "function(doc) { if (doc.size) {return (doc.size); }} ";

		final Context ctx = new ContextFactory().enterContext();
		final Scriptable scope = ctx.initStandardObjects();

		final Function function = ctx.compileFunction(scope, source, "fun", 0, null);

		final Object[] args = new Object[] { new Thing(), "b", "c" };
		
		final Object obj = function.call(ctx, scope, null, args);
		System.err.println(obj);
	}

}
