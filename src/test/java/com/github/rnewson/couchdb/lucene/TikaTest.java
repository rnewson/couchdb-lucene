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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.lucene.document.Document;
import org.junit.Before;
import org.junit.Test;

public class TikaTest {

	private Tika tika;
	private Document doc;

	@Before
	public void setup() {
		tika = new Tika();
		doc = new Document();
	}

	@Test
	public void testPDF() throws IOException {
		parse("paxos-simple.pdf", "application/pdf");
		assertThat(doc.getField(Config.BODY), not(nullValue()));
	}

	@Test
	public void testXML() throws IOException {
		parse("example.xml", "text/xml");
		assertThat(doc.getField(Config.BODY), not(nullValue()));
	}

	@Test
	public void testEnglish() throws IOException {
		tika.parse(new ByteArrayInputStream("english text goes here".getBytes()), "text/plain", doc);
		assertThat(doc.getField("dc.language").stringValue(), is("en"));
	}
	
	@Test
	public void testGerman() throws IOException {
		tika.parse(new ByteArrayInputStream("Alle Menschen sind frei und gleich".getBytes()), "text/plain", doc);
		assertThat(doc.getField("dc.language").stringValue(), is("de"));
	}

	@Test
	public void testFrench() throws IOException {
		tika.parse(new ByteArrayInputStream("Me permettez-vous, dans ma gratitude".getBytes()), "text/plain", doc);
		assertThat(doc.getField("dc.language").stringValue(), is("fr"));
	}

	private void parse(final String resource, final String type) throws IOException {
		final InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
		try {
			tika.parse(in, type, doc);
		} finally {
			in.close();
		}
	}

}
