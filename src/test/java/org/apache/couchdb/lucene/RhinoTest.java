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
		ctx.setLanguageVersion(170);
		final Scriptable scope = ctx.initStandardObjects();

		final Function function = ctx.compileFunction(scope, source, "fun", 0, null);

		final Object[] args = new Object[] { new Thing(), "b", "c" };

		Object obj = function.call(ctx, scope, null, args);
		System.err.println(obj);
		
		final String source2 = "function myobj(arg) {this.size=12}";
		
		final Object o = Context.jsToJava(source2, Object.class);
		System.err.println(o);
		System.err.println(o.getClass());
		
		final Object o2 = Context.javaToJS(new Thing(), scope);
		System.err.println(o2);
		System.err.println(o2.getClass());

		obj = ctx.evaluateString(scope, source2, "fun2", 0, null);
		System.err.println(obj);
	}

}
