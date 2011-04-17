package com.github.rnewson.couchdb.lucene.pojo;

/**
 * Copyright 2011 Robert Newson
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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.codehaus.jackson.JsonProcessingException;
import org.junit.Test;

public class DatabaseInfoTest extends JacksonTest {

	@Test
	public void testDatabase() throws JsonProcessingException, IOException {
		final String str = "{\"db_name\":\"db1\",\"doc_count\":1192,\"doc_del_count\":0,"
				+ "\"update_seq\":1195,\"purge_seq\":0,\"compact_running\":false,"
				+ "\"disk_size\":5226596,\"instance_start_time\":\"1302962250150529\","
				+ "\"disk_format_version\":5,\"committed_update_seq\":1195}";
		final DatabaseInfo dbinfo = mapper.readValue(str, DatabaseInfo.class);
		assertThat(dbinfo.getName(), is("db1"));
		assertThat(dbinfo.getUpdateSequence(), is(1195L));
	}
}
