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

package com.github.rnewson.couchdb.lucene;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.document.Document;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

import java.io.IOException;
import java.io.InputStream;

import static com.github.rnewson.couchdb.lucene.util.Utils.text;

public final class Tika {

    public static final Tika INSTANCE = new Tika();

    private static final String DC = "_dc.";

    private static final Logger log = LoggerFactory.getLogger(Tika.class);

    private final org.apache.tika.Tika tika = new org.apache.tika.Tika();

    private Tika() {
        tika.setMaxStringLength(-1);
    }

    public void parse(final InputStream in, final String contentType, final String fieldName, final Document doc)
            throws IOException {
        final Metadata md = new Metadata();
        md.set(HttpHeaders.CONTENT_TYPE, contentType);

        try {
            // Add body text.
            doc.add(text(fieldName, tika.parseToString(in, md), false));
        } catch (final IOException e) {
            log.warn("Failed to index an attachment.", e);
            return;
        } catch (final TikaException e) {
            log.warn("Failed to parse an attachment.", e);
            return;
        }

        // Add DC attributes.
        addDublinCoreAttributes(md, doc);
    }

    private void addAttribute(final String namespace, final String attributeName, final Metadata md, final Document doc) {
        if (md.get(attributeName) != null) {
            doc.add(text(namespace + attributeName, md.get(attributeName), false));
        }
    }

    private void addAttribute(final String namespace, final Property property, final Metadata md, final Document doc) {
        if (md.get(property) != null) {
            doc.add(text(namespace + property.getName(), md.get(property), false));
        }
    }

    private void addDublinCoreAttributes(final Metadata md, final Document doc) {
        addAttribute(DC, DublinCore.CONTRIBUTOR, md, doc);
        addAttribute(DC, DublinCore.COVERAGE, md, doc);
        addAttribute(DC, DublinCore.CREATOR, md, doc);
        addAttribute(DC, DublinCore.DATE, md, doc);
        addAttribute(DC, DublinCore.DESCRIPTION, md, doc);
        addAttribute(DC, DublinCore.FORMAT, md, doc);
        addAttribute(DC, DublinCore.IDENTIFIER, md, doc);
        addAttribute(DC, DublinCore.LANGUAGE, md, doc);
        addAttribute(DC, DublinCore.MODIFIED, md, doc);
        addAttribute(DC, DublinCore.PUBLISHER, md, doc);
        addAttribute(DC, DublinCore.RELATION, md, doc);
        addAttribute(DC, DublinCore.RIGHTS, md, doc);
        addAttribute(DC, DublinCore.SOURCE, md, doc);
        addAttribute(DC, DublinCore.SUBJECT, md, doc);
        addAttribute(DC, DublinCore.TITLE, md, doc);
        addAttribute(DC, DublinCore.TYPE, md, doc);
    }
}
