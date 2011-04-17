package com.github.rnewson.couchdb.lucene.pojo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.apache.lucene.document.Field.TermVector;
import org.junit.Test;

public class ViewSettingsTest extends JacksonTest {

	@Test
	public void testBoost() throws Exception {
		final String str = "{\"boost\": 0.5}";
		final ViewSettings view = mapper.readValue(str, ViewSettings.class);
		assertThat(view.getBoost(), is(0.5f));
	}

	@Test
	public void testTermVectorUpper() throws Exception {
		final String str = "{\"termvector\": \"WITH_POSITIONS\"}";
		final ViewSettings view = mapper.readValue(str, ViewSettings.class);
		assertThat(view.getTermvector(), is(TermVector.WITH_POSITIONS));
	}
	
}
