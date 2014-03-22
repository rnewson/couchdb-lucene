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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class DesignDocumentTest {

    @Test(expected = IllegalArgumentException.class)
    public void notDesignDocument() throws Exception {
        new DesignDocument(new JSONObject("{_id:\"hello\"}"));
    }

    @Test
    public void noViews() throws Exception {
        final DesignDocument ddoc = new DesignDocument(new JSONObject("{_id:\"_design/hello\"}"));
        assertThat(ddoc.getAllViews().size(), is(0));
    }

    @Test
    public void views() throws Exception {
        final JSONObject view = new JSONObject();
        view.put("index", "function(doc) { return null; }");

        final JSONObject fulltext = new JSONObject();
        fulltext.put("foo", view);

        final JSONObject json = new JSONObject();
        json.put("_id", "_design/hello");
        json.put("fulltext", fulltext);

        final DesignDocument ddoc = new DesignDocument(json);
        assertThat(ddoc.getView("foo"), notNullValue());
        assertThat(ddoc.getAllViews().size(), is(1));
    }

}
