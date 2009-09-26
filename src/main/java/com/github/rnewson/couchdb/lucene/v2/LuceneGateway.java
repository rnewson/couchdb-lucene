package com.github.rnewson.couchdb.lucene.v2;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.IndexReaderWarmer;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Holds important stateful Lucene objects (Writer and Reader) and provides
 * appropriate locking.
 * 
 * @author rnewson
 * 
 */
final class LuceneGateway {

    private static class LuceneHolder {

        private final Directory dir;

        private IndexReader reader;

        private final boolean realtime;

        private final IndexWriter writer;

        private LuceneHolder(final Directory dir, final boolean realtime) throws IOException {
            this.dir = dir;
            this.realtime = realtime;
            this.writer = newWriter();
            this.reader = newReader();
            this.reader.incRef();
        }

        private void close() throws IOException {
            reader.decRef();
            writer.rollback();
        }

        private IndexReader newReader() throws IOException {
            return realtime ? getIndexWriter().getReader() : IndexReader.open(dir, true);
        }

        private IndexWriter newWriter() throws IOException {
            final IndexWriter result = new IndexWriter(dir, Constants.ANALYZER, MaxFieldLength.UNLIMITED);
            result.setMergeFactor(5);
            result.setMergedSegmentWarmer(newWarmer());
            return result;
        }

        private IndexReaderWarmer newWarmer() {
            return new IndexReaderWarmer() {

                @Override
                public void warm(final IndexReader reader) throws IOException {
                    // Prewarm sequence (is this "insane"?)
                    FieldCache.DEFAULT.getLongs(reader, Constants.SEQ);

                    // TODO allow clients to specify more fields.
                }
            };
        }

        synchronized IndexReader borrowReader(final boolean staleOk) throws IOException {
            if (!staleOk)
                reopenReader();
            reader.incRef();
            return reader;
        }

        synchronized IndexSearcher borrowSearcher(final boolean staleOk) throws IOException {
            final IndexReader reader = borrowReader(staleOk);
            return new IndexSearcher(reader);
        }

        IndexWriter getIndexWriter() throws IOException {
            return writer;
        }

        void reopenReader() throws IOException {
            final IndexReader newReader = reader.reopen();
            if (reader != newReader) {
                final IndexReader oldReader = reader;
                reader = newReader;
                oldReader.decRef();
            }
        }

        synchronized void returnReader(final IndexReader reader) throws IOException {
            reader.decRef();
        }

        synchronized void returnSearcher(final IndexSearcher searcher) throws IOException {
            returnReader(searcher.getIndexReader());
        }
    }

    interface ReaderCallback<T> {
        public T callback(final IndexReader reader) throws IOException;
    }

    interface SearcherCallback<T> {
        public T callback(final IndexSearcher searcher) throws IOException;
    }

    interface WriterCallback<T> {
        public T callback(final IndexWriter writer) throws IOException;
    }

    private final Map<ViewSignature, LuceneHolder> holders = new LinkedHashMap<ViewSignature, LuceneHolder>();

    private final File baseDir;

    private final boolean realtime;

    LuceneGateway(final File baseDir, final boolean realtime) {
        this.baseDir = baseDir;
        this.realtime = realtime;
    }

    private synchronized LuceneHolder getHolder(final ViewSignature viewSignature) throws IOException {
        LuceneHolder result = holders.get(viewSignature);
        if (result == null) {
            final File dir = viewSignature.toFile(baseDir);
            if (!dir.exists() && !dir.mkdirs())
                throw new IOException("Could not make " + dir);
            result = new LuceneHolder(FSDirectory.open(dir), realtime);
            holders.put(viewSignature, result);
        }
        return result;
    }

    <T> T withReader(final ViewSignature viewSignature, final boolean staleOk, final ReaderCallback<T> callback) throws IOException {
        final LuceneHolder holder = getHolder(viewSignature);
        final IndexReader reader = holder.borrowReader(staleOk);
        try {
            return callback.callback(reader);
        } finally {
            holder.returnReader(reader);
        }
    }

    <T> T withSearcher(final ViewSignature viewSignature, final boolean staleOk, final SearcherCallback<T> callback)
            throws IOException {
        final LuceneHolder holder = getHolder(viewSignature);
        final IndexSearcher searcher = holder.borrowSearcher(staleOk);
        try {
            return callback.callback(searcher);
        } finally {
            holder.returnSearcher(searcher);
        }
    }

    <T> T withWriter(final ViewSignature viewSignature, final WriterCallback<T> callback) throws IOException {
        LuceneHolder holder = getHolder(viewSignature);
        final IndexWriter writer = holder.getIndexWriter();
        try {
            return callback.callback(writer);
        } catch (final OutOfMemoryError e) {
            synchronized (holders) {
                holder = holders.remove(viewSignature);
                holder.close();
            }
            throw e;
        }
    }

    void deleteIndex(final ViewSignature viewSignature) throws IOException {

    }

    void createIndex(final ViewSignature viewSignature) throws IOException {

    }

    synchronized void close() throws IOException {
        final Iterator<LuceneHolder> it = holders.values().iterator();
        while (it.hasNext()) {
            it.next().close();
            it.remove();
        }
    }

}
