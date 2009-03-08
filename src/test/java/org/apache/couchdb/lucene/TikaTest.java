package org.apache.couchdb.lucene;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

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

	private void parse(final String resource, final String type) throws IOException {
		final InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
		try {
			tika.parse(in, type, doc);
		} finally {
			in.close();
		}
	}

}
