package com.github.rnewson.couchdb.lucene.pojo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class ViewResultTest extends JacksonTest {

	@Test
	public void testViewResult() throws Exception {
		final String str = "{\"total_rows\":1192,\"offset\":1,\"rows\":[\n"
				+ "{\"id\":\"1f5195d6bc0a5973315e4a2710000d39\",\"key\":\"1f5195d6bc0a5973315e4a2710000d39\","
				+ "\"value\":{\"rev\":\"2-7051cbe5c8faecd085a3fa619e6e6337\"},\"doc\":{\"_id\":\"foo\"}},\n"
				+ "{\"id\":\"78596fc515b05ce7651b6d9c0800068a\",\"key\":\"78596fc515b05ce7651b6d9c0800068a\","
				+ "\"value\":{\"rev\":\"1-4ce1e301f5d615c8f06891016d23365a\"}}\n"
				+ "]}";
		final ViewResult results = mapper.readValue(str, ViewResult.class);
		assertThat(results.getTotalRows(), is(1192L));
		assertThat(results.getOffset(), is(1L));
		assertThat(results.getRow(0).getId(),
				is("1f5195d6bc0a5973315e4a2710000d39"));
		assertThat(results.getRow(0).getDoc().getId(), is("foo"));
	}

}
