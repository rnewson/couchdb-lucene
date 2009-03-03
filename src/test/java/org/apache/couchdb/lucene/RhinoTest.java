package org.apache.couchdb.lucene;

import static org.junit.Assert.assertThat;

import org.hamcrest.CoreMatchers;
import org.junit.Test;

public class RhinoTest {

	@Test
	public void testRhino() {
		final Rhino rhino = new Rhino("function(doc) { delete doc.deleteme; return doc; }");
		final String doc = "{\"deleteme\":\"true\", \"size\":13}";
		assertThat((Double)rhino.parse(doc).get("size", null), CoreMatchers.is(13.0));
		rhino.close();
	}

}
