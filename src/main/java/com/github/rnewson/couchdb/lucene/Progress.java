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

    public long getSeq(final String dbname) {
        final Field field = progress.getField(seqField(dbname));
        return field == null ? 0 : Long.parseLong(field.stringValue());
    }

    public String getSignature(final String dbname) {
        final Field field = progress.getField(sigField(dbname));
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

    public void remove(final String dbname) {
        progress.removeFields(seqField(dbname));
        progress.removeFields(sigField(dbname));
    }

    public void update(final String dbname, final String sig, final long seq) {
        // Update seq.
        progress.removeFields(seqField(dbname));
        progress.add(new Field(seqField(dbname), Long.toString(seq), Store.YES, Field.Index.NO));

        // Update sig.
        progress.removeFields(sigField(dbname));
        progress.add(new Field(sigField(dbname), sig, Store.YES, Field.Index.NO));
    }

    private Document newDocument() {
        final Document result = new Document();
        // Add unique identifier.
        result.add(new Field(PROGRESS_KEY, PROGRESS_VALUE, Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
        return result;
    }

    private String seqField(final String dbname) {
        return dbname + "-seq";
    }

    private String sigField(final String dbname) {
        return dbname + "-sig";
    }

}
