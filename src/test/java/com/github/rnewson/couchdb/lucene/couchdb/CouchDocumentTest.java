package com.github.rnewson.couchdb.lucene.couchdb;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import net.sf.json.JSONObject;

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

public class CouchDocumentTest {

    @Test(expected = IllegalArgumentException.class)
    public void notValidDocument() {
        new CouchDocument(JSONObject.fromObject("{}"));
    }

    @Test
    public void validDocument() {
        final CouchDocument doc = new CouchDocument(JSONObject.fromObject("{_id:\"hello\"}"));
        assertThat(doc.getId(), is("hello"));
    }

    @Test
    public void asJson() {
        final JSONObject json = JSONObject.fromObject("{_id:\"hello\"}");
        final CouchDocument doc = new CouchDocument(json);
        assertThat(doc.asJson(), is(json));
    }

}
