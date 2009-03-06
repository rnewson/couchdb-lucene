package org.apache.couchdb.lucene;

import static org.junit.Assert.assertThat;

import org.hamcrest.CoreMatchers;
import org.junit.Test;

public class RhinoTest {

	@Test
	public void testRhino() throws Exception {
		final Rhino rhino = new Rhino("function(doc) { delete doc.deleteme; doc.size++; return doc; }");
		final String doc = "{\"deleteme\":\"true\", \"size\":13}";
		assertThat(rhino.parse(doc), CoreMatchers.equalTo("{\"size\":14}"));
		rhino.close();
	}

}
