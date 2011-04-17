package com.github.rnewson.couchdb.lucene.pojo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

/**
 * Copyright 2010 Robert Newson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

public class DocumentTest extends JacksonTest {

	@Test
	public void testDocument() throws JsonProcessingException, IOException {
		final String str = "{\"_id\":\"foo\",\"_rev\":\"2-7051cbe5c8faecd085a3fa619e6e6337\"}";
		final Document doc = mapper.readValue(str, Document.class);
		assertThat(doc.getId(), is("foo"));
		assertThat(doc.isDeleted(), is(false));
	}

	@Test
	public void testDeletedDocument() throws JsonProcessingException,
			IOException {
		final String str = "{\"_id\":\"foo\",\"_rev\":\"2-7051cbe5c8faecd085a3fa619e6e6337\",\"_deleted\":true}";
		final Document doc = mapper.readValue(str, Document.class);
		assertThat(doc.getId(), is("foo"));
		assertThat(doc.isDeleted(), is(true));
	}

}
