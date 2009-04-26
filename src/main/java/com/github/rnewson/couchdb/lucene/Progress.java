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

import java.io.IOException;
import java.util.ArrayList;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;

public final class Progress {

    public static final String NO_SIGNATURE = "";

    private static final String PROGRESS_KEY = "_couchdb";

    private static final String PROGRESS_VALUE = "status";

    private static final Term PROGRESS_TERM = new Term(PROGRESS_KEY, PROGRESS_VALUE);

    private Document progress = newDocument();

    public Progress() {
    }

    public long getSeq(final String view_name) {
        final Field field = progress.getField(seqField(view_name));
        return field == null ? 0 : Long.parseLong(field.stringValue());
    }

    public String getSignature(final String view_name) {
        final Field field = progress.getField(sigField(view_name));
        return field == null ? NO_SIGNATURE : field.stringValue();
    }

    public void load(final IndexReader reader) throws IOException {
        progress = newDocument();

        final TermDocs termDocs = reader.termDocs(PROGRESS_TERM);
        try {
            while (termDocs.next()) {
                final int doc = termDocs.doc();
                if (!reader.isDeleted(doc)) {
                    progress = reader.document(doc);
                }
            }
        } finally {
            termDocs.close();
        }
    }

    public void save(final IndexWriter writer) throws IOException {
        writer.updateDocument(PROGRESS_TERM, progress);
    }

    public void removeView(final String view_name) {
        progress.removeFields(seqField(view_name));
        progress.removeFields(sigField(view_name));
    }

    public void removeDatabase(final String dbname) {
        final String prefix = dbname + "/";
        for (final Object obj : new ArrayList(progress.getFields())) {
            final Field field = (Field) obj;
            if (field.name().startsWith(prefix)) {
                progress.removeField(field.name());
            }
        }
    }

    public void update(final String view_name, final String sig, final long seq) {
        // Update seq.
        progress.removeFields(seqField(view_name));
        progress.add(new Field(seqField(view_name), Long.toString(seq), Store.YES, Field.Index.NO));

        // Update sig.
        progress.removeFields(sigField(view_name));
        progress.add(new Field(sigField(view_name), sig, Store.YES, Field.Index.NO));
    }

    private Document newDocument() {
        final Document result = new Document();
        // Add unique identifier.
        result.add(new Field(PROGRESS_KEY, PROGRESS_VALUE, Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
        return result;
    }

    private String seqField(final String view_name) {
        return view_name + "-seq";
    }

    private String sigField(final String view_name) {
        return view_name + "-sig";
    }

}
