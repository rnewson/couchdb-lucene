package org.apache.couchdb.lucene;

import static org.junit.Assert.assertThat;

import org.hamcrest.CoreMatchers;
import org.junit.Test;

public class RhinoTest {

	@Test
	public void testRhino() {
		final Rhino rhino = new Rhino("function(doc){return doc.size}");
		final String doc = "{\"size\":13}";
		assertThat(rhino.parse(doc), CoreMatchers.is("13.0"));
		rhino.close();
	}

}
