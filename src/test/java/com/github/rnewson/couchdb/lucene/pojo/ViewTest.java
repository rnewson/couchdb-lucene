package com.github.rnewson.couchdb.lucene.pojo;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class ViewTest extends JacksonTest {

	@Test
	public void testView() throws Exception {
		final String str = "{\"index\":\"bar\"}";
		final View view = mapper.readValue(str, View.class);
		assertThat(view.getIndex(), is("bar"));
		assertThat(view.getAnalyzer(), is("standard"));
		assertThat(view.getDefaults(), notNullValue());
	}

	@Test
	public void testDifferentAnalyzer() throws Exception {
		final String str = "{\"analyzer\":\"french\"}";
		final View view = mapper.readValue(str, View.class);
		assertThat(view.getAnalyzer(), is("french"));
	}

}
