package com.github.rnewson.couchdb.lucene;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
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

    interface ReaderCallback<T> {
        public T callback(final IndexReader reader) throws IOException;
    }

    interface SearcherCallback<T> {
        public T callback(final IndexSearcher searcher, final String etag) throws IOException;
    }

    interface WriterCallback<T> {
        public T callback(final IndexWriter writer) throws IOException;
    }

    private final File baseDir;

    private static class Holder {
        private IndexWriter writer;
        private String etag;
    }

    private final Map<ViewSignature, Holder> holders = new HashMap<ViewSignature, Holder>();

    LuceneGateway(final File baseDir) {
        this.baseDir = baseDir;
    }

    private String newEtag() {
        return Long.toHexString(System.nanoTime());
    }

    private synchronized Holder getHolder(final ViewSignature viewSignature) throws IOException {
        Holder result = holders.get(viewSignature);
        if (result == null) {
            final File dir = viewSignature.toFile(baseDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Could not make " + dir);
            }
            result = new Holder();
            result.writer = newWriter(FSDirectory.open(dir));
            result.etag = newEtag();
            holders.put(viewSignature, result);
        }
        return result;
    }

    private IndexWriter newWriter(final Directory dir) throws IOException {
        final IndexWriter result = new IndexWriter(dir, Constants.ANALYZER, MaxFieldLength.UNLIMITED);
        result.setMergeFactor(5);
        return result;
    }

    synchronized void close() throws IOException {
        final Iterator<Holder> it = holders.values().iterator();
        while (it.hasNext()) {
            it.next().writer.rollback();
            it.remove();
        }
    }

    <T> T withReader(final ViewSignature viewSignature, final ReaderCallback<T> callback) throws IOException {
        return callback.callback(getHolder(viewSignature).writer.getReader());
    }

    <T> T withSearcher(final ViewSignature viewSignature, final SearcherCallback<T> callback) throws IOException {
        final Holder holder = getHolder(viewSignature);
        return callback.callback(new IndexSearcher(holder.writer.getReader()), holder.etag);
    }

    <T> T withWriter(final ViewSignature viewSignature, final WriterCallback<T> callback) throws IOException {
        boolean oom = false;
        try {
            return callback.callback(getHolder(viewSignature).writer);
        } catch (final OutOfMemoryError e) {
            oom = true;
            throw e;
        } finally {
            synchronized (this) {
                if (oom) {
                    holders.remove(viewSignature).writer.rollback();
                } else {
                    getHolder(viewSignature).etag = newEtag();
                }
            }
        }
    }

}
