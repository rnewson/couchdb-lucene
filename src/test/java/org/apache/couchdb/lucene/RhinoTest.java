package org.apache.couchdb.lucene;

import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;

public class RhinoTest {

	@Test
	public void testSimpleEval() {
		final String indexing_function = "function(doc) { if (doc.size) { emit_int(doc.size); }  } ";

		final Rhino rhino = new Rhino();
		Object obj = rhino.evaluate(indexing_function);
		System.err.println(obj);

		final Context ctx = new ContextFactory().enterContext();
		final Scriptable scope = ctx.initStandardObjects();
		
		final Object[] args = new Object[] { "a", "b", "c" };


		obj = ctx.evaluateString(scope, indexing_function, "<fun>", 0, null);
		System.err.println(obj);
	}

}
