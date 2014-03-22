/*
 * Copyright Robert Newson
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

package com.github.rnewson.couchdb.lucene.couchdb;

import org.json.JSONObject;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class CouchDocumentTest {

    @Test(expected = IllegalArgumentException.class)
    public void notValidDocument() throws Exception {
        new CouchDocument(new JSONObject("{}"));
    }

    @Test
    public void validDocument() throws Exception {
        final CouchDocument doc = new CouchDocument(new JSONObject("{_id:\"hello\"}"));
        assertThat(doc.getId(), is("hello"));
    }

    @Test
    public void asJson() throws Exception {
        final JSONObject json = new JSONObject("{_id:\"hello\"}");
        final CouchDocument doc = new CouchDocument(json);
        assertThat(doc.asJson(), is(json));
    }

}
