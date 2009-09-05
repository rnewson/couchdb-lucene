package com.github.rnewson.couchdb.lucene.v2;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;

final class LuceneHolder {

    private final Directory dir;

    private IndexWriter writer;

    private IndexReader reader;

    private IndexSearcher searcher;

    private final boolean realtime;

    LuceneHolder(final Directory dir, final boolean realtime) {
        this.dir = dir;
        this.realtime = realtime;
    }

    synchronized IndexWriter getIndexWriter() throws IOException {
        if (writer == null) {
            writer = new IndexWriter(dir, Constants.ANALYZER, MaxFieldLength.UNLIMITED);
        }
        return writer;
    }

    void createIndex() throws Exception {
        getIndexWriter();
    }

    synchronized IndexReader borrowReader() throws IOException {
        if (reader == null) {
            if (realtime)
                reader = getIndexWriter().getReader();
            else
                reader = IndexReader.open(dir, true);
            // Prevent closure.
            reader.incRef();
        }
        reader.incRef();
        return reader;
    }

    Directory getDirectory() {
        return dir;
    }

    synchronized void returnReader(final IndexReader reader) throws IOException {
        reader.decRef();
    }

    synchronized IndexSearcher borrowSearcher() throws IOException {
        if (searcher == null) {
            searcher = new IndexSearcher(reader);
        }
        searcher.getIndexReader().incRef();
        return searcher;
    }

    synchronized void returnSearcher(final IndexSearcher searcher) throws IOException {
        searcher.getIndexReader().decRef();
    }

    void reopenReader() throws IOException {
        final IndexReader oldReader;
        synchronized (this) {
            oldReader = reader;
        }

        final IndexReader newReader = oldReader.reopen();

        if (reader != newReader) {
            synchronized (this) {
                reader = newReader;
                searcher = new IndexSearcher(reader);
                oldReader.decRef();
            }
        }
    }

}
