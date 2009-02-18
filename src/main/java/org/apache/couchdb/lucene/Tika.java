package org.apache.couchdb.lucene;

import static org.apache.couchdb.lucene.Utils.text;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.document.Document;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParsingReader;

public final class Tika {

	public void parse(final InputStream in, final String contentType, final Document doc) {
		final AutoDetectParser parser = new AutoDetectParser();
		final Metadata md = new Metadata();

		final Reader reader = new ParsingReader(parser, in, md);
		final String body;
		try {
			try {
				body = IOUtils.toString(reader);
			} finally {
				reader.close();
			}
		} catch (final IOException e) {
			return;
		}

		System.err.printf("body: %s, md: %s\n", body, md);

		doc.add(text(Config.BODY, body, false));

		if (md.get(Metadata.TITLE) != null) {
			doc.add(text(Config.TITLE, md.get(Metadata.TITLE), true));
		}

		if (md.get(Metadata.AUTHOR) != null) {
			doc.add(text(Config.AUTHOR, md.get(Metadata.AUTHOR), true));
		}
	}

}
