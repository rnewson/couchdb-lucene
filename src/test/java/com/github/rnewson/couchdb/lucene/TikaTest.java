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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.io.InputStream;

import org.apache.lucene.document.Document;
import org.junit.Before;
import org.junit.Test;

public class TikaTest {

    private Document doc;

    @Before
    public void setup() {
        doc = new Document();
    }

    @Test
    public void testPDF() throws IOException {
        parse("paxos-simple.pdf", "application/pdf", "foo");
        assertThat(doc.getFieldable("foo"), not(nullValue()));
    }

    @Test
    public void testXML() throws IOException {
        parse("example.xml", "text/xml", "bar");
        assertThat(doc.getFieldable("bar"), not(nullValue()));
    }

    @Test
    public void testWord() throws IOException {
        parse("example.doc", "application/msword", "bar");
        assertThat(doc.getFieldable("bar"), not(nullValue()));
        assertThat(doc.get("bar"), containsString("The express mission of the organization"));
    }

    private void parse(final String resource, final String type, final String field) throws IOException {
        final InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
        try {
            Tika.INSTANCE.parse(in, type, field, doc);
        } finally {
            in.close();
        }
    }

}
