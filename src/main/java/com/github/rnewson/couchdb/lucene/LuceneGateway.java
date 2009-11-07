package com.github.rnewson.couchdb.lucene;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexDeletionPolicy;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;

import com.github.rnewson.couchdb.lucene.util.Constants;

/**
 * Holds important stateful Lucene objects (Writer and Reader) and provides
 * appropriate locking.
 * 
 * @author rnewson
 * 
 */
final class LuceneGateway {

    private static class Holder {
        private String etag;
        private IndexWriter writer;
    }

    interface ReaderCallback<T> {
        public T callback(final IndexReader reader) throws IOException;
    }

    interface SearcherCallback<T> {
        public T callback(final IndexSearcher searcher, final String etag) throws IOException;
    }

    interface WriterCallback {
        /**
         * @return if index was modifed (add, update, delete)
         */
        public boolean callback(final IndexWriter writer) throws IOException;
    }

    private final File baseDir;

    private final Map<ViewSignature, Holder> holders = new HashMap<ViewSignature, Holder>();

    LuceneGateway(final File baseDir) {
        this.baseDir = baseDir;
    }

    private synchronized Holder getHolder(final ViewSignature viewSignature) throws IOException {
        Holder result = holders.get(viewSignature);
        if (result == null) {
            final File dir = viewSignature.toViewDir(baseDir);
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

    private String newEtag() {
        return Long.toHexString(System.nanoTime());
    }

    private IndexWriter newWriter(final Directory dir) throws IOException {
        final IndexWriter result = new IndexWriter(dir, Constants.ANALYZER, MaxFieldLength.UNLIMITED);
        result.setMergeFactor(5);
        return result;
    }

    public synchronized void close() throws IOException {
        final Iterator<Holder> it = holders.values().iterator();
        while (it.hasNext()) {
            it.next().writer.rollback();
            it.remove();
        }
    }

    public <T> T withReader(final ViewSignature viewSignature, final ReaderCallback<T> callback) throws IOException {
        return callback.callback(getHolder(viewSignature).writer.getReader());
    }

    public <T> T withSearcher(final ViewSignature viewSignature, final SearcherCallback<T> callback) throws IOException {
        final Holder holder = getHolder(viewSignature);
        return callback.callback(new IndexSearcher(holder.writer.getReader()), holder.etag);
    }

    public void withWriter(final ViewSignature viewSignature, final WriterCallback callback) throws IOException {
        try {
            if (callback.callback(getHolder(viewSignature).writer)) {
                getHolder(viewSignature).etag = newEtag();
            }
        } catch (final OutOfMemoryError e) {
            holders.remove(viewSignature).writer.rollback();
            throw e;
        }
    }

    public void deleteDatabase(final String databaseName) throws IOException {
        // Remove all active holders for this database.
        final Set<Holder> oldHolders = new HashSet<Holder>();
        synchronized (this) {
            final Iterator<Entry<ViewSignature, Holder>> it = holders.entrySet().iterator();
            while (it.hasNext()) {
                final Entry<ViewSignature, Holder> entry = it.next();
                if (databaseName.equals(entry.getKey().getDatabaseName())) {
                    oldHolders.add(entry.getValue());
                    it.remove();
                }
            }
        }
        // Close all file handles.
        for (final Holder holder : oldHolders) {
            holder.writer.rollback();
        }
        // Purge from disk.
        FileUtils.deleteDirectory(new File(baseDir, databaseName));
    }

}
