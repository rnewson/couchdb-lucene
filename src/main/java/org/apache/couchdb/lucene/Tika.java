package org.apache.couchdb.lucene;

import static org.apache.couchdb.lucene.Utils.text;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParsingReader;

public final class Tika {

	private static final Logger log = LogManager.getLogger(Tika.class);

	public void parse(final InputStream in, final String contentType, final Document doc) {
		final AutoDetectParser parser = new AutoDetectParser();
		final Metadata md = new Metadata();
		md.set(Metadata.CONTENT_TYPE, contentType);

		final Reader reader = new ParsingReader(parser, in, md);
		final String body;
		try {
			try {
				body = IOUtils.toString(reader);
			} finally {
				reader.close();
			}
		} catch (final IOException e) {
			log.warn("Failed to index an attachment.", e);
			return;
		}

		doc.add(text(Config.BODY, body, false));

		if (md.get(Metadata.TITLE) != null) {
			doc.add(text(Config.TITLE, md.get(Metadata.TITLE), true));
		}

		if (md.get(Metadata.AUTHOR) != null) {
			doc.add(text(Config.AUTHOR, md.get(Metadata.AUTHOR), true));
		}
	}

}
