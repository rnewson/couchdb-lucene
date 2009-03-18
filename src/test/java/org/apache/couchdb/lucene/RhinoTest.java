package org.apache.couchdb.lucene;

/**
 * Copyright 2009 Robert Newson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
