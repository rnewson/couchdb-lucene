package com.github.rnewson.couchdb.lucene;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class PathPartsTest {
	
	@Test
	public void testSearchPath() {
		final PathParts parts = new PathParts("/local/db1/_design/foo/by_subject");
		assertThat(parts.getKey(), is("local"));
		assertThat(parts.getDatabaseName(), is("db1"));
		assertThat(parts.getDesignDocumentName(), is("_design/foo"));
		assertThat(parts.getViewName(), is("by_subject"));
	}

}
