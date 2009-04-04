package com.github.rnewson.couchdb.lucene;

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

import net.sf.json.JSONObject;

import org.apache.lucene.document.Document;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

public class RhinoTest {

    @Test
    public void testRhino() throws Exception {
        final Rhino rhino = new Rhino("function(doc) { var ret = new Document(); "
                + "ret.field(\"foo\", doc.size); return ret }");
        final String doc = "{\"deleteme\":\"true\", \"size\":13}";
        Document[] ret = rhino.map(doc);
        assertThat(ret.length, CoreMatchers.equalTo(1));
        assertThat(ret[0].getField("foo"), CoreMatchers.notNullValue());
        rhino.close();
    }

    @Test
    public void testNoReturn() throws Exception {
        final Rhino rhino = new Rhino("function(doc) {}");
        Document[] ret = rhino.map("{}");
        assertThat(ret.length, CoreMatchers.equalTo(0));
        rhino.close();
    }

    @Test(expected = RuntimeException.class)
    public void testBadReturn() throws Exception {
        final Rhino rhino = new Rhino("function(doc) {return 1;}");
        rhino.map("{}");
        rhino.close();
    }

    @Test
    public void testCtor() throws Exception {
        final Rhino rhino = new Rhino("function(doc) { return new Document(\"foo\", 1); }");
        Document[] ret = rhino.map("{}");
        assertThat(ret.length, CoreMatchers.equalTo(1));
        assertThat(ret[0].getField("foo"), CoreMatchers.notNullValue());
        rhino.close();
    }

    @Test
    public void testMultipleReturn() throws Exception {
        final Rhino rhino = new Rhino("function(doc) { " + "var ret = []; "
                + "for(var v in doc) {var d = new Document(); d.field(v, doc[v]); ret.push(d)} " + "return ret; " + "}");
        Document[] ret = rhino.map("{\"foo\": 1, \"bar\": 2}");
        assertThat(ret.length, CoreMatchers.equalTo(2));
        rhino.close();
    }

    @Test
    public void testDate() throws Exception {
        final Rhino rhino = new Rhino("function(doc) { var ret = new Document(); "
                + "ret.date(\"bar\", \"2009-01-0T00:00:00Z\"); return ret;}");
        Document[] ret = rhino.map("{\"foo\": 1, \"bar\": 2}");
        assertThat(ret.length, CoreMatchers.equalTo(1));
        assertThat(ret[0].getField("bar"), CoreMatchers.notNullValue());
        rhino.close();
    }
}
