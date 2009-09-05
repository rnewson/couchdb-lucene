package com.github.rnewson.couchdb.lucene.v2;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LogByteSizeMergePolicy;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;

final class LuceneHolder {

    private final Directory dir;

    private IndexReader reader;

    private final boolean realtime;

    private final IndexWriter writer;

    LuceneHolder(final Directory dir, final boolean realtime) throws IOException {
        this.dir = dir;
        this.realtime = realtime;
        this.writer = newWriter();
        this.reader = newReader();
        this.reader.incRef();
    }

    private IndexReader newReader() throws IOException {
        if (realtime) {
            return getIndexWriter().getReader();
        }
        return IndexReader.open(dir, true);
    }

    private IndexWriter newWriter() throws IOException {
        final IndexWriter result = new IndexWriter(dir, Constants.ANALYZER, MaxFieldLength.UNLIMITED);
        final LogByteSizeMergePolicy mp = new LogByteSizeMergePolicy(result);
        mp.setMergeFactor(5);
        mp.setMaxMergeMB(1000);
        mp.setUseCompoundFile(false);
        result.setMergePolicy(mp);
        result.setRAMBufferSizeMB(16);
        return result;
    }

    synchronized IndexReader borrowReader() throws IOException {
        reader.incRef();
        return reader;
    }

    synchronized IndexSearcher borrowSearcher() throws IOException {
        final IndexReader reader = borrowReader();
        return new IndexSearcher(reader);
    }

    IndexWriter getIndexWriter() throws IOException {
        return writer;
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
                oldReader.decRef();
            }
        }
    }

    synchronized void returnReader(final IndexReader reader) throws IOException {
        reader.decRef();
    }

    synchronized void returnSearcher(final IndexSearcher searcher) throws IOException {
        returnReader(searcher.getIndexReader());
    }

}
