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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

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
        private boolean dirty;
        private IndexWriter writer;
        private IndexReader reader;
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
        result.setUseCompoundFile(false);
        return result;
    }

    public synchronized void close() throws IOException {
        for (final Holder holder : holders.values()) {
            holder.reader.clone();
            holder.writer.rollback();
        }
        holders.clear();
    }

    public <T> T withReader(final ViewSignature viewSignature, final boolean staleOk, final ReaderCallback<T> callback)
            throws IOException {
        final Holder holder = getHolder(viewSignature);
        final IndexReader reader;
        
        synchronized (holder) {
            if (holder.reader == null) {
                holder.reader = holder.writer.getReader();
                holder.etag = newEtag();
                holder.dirty = false;
                holder.reader.incRef(); // keep the reader open.
            }

            if (!staleOk) {
                holder.reader.decRef(); // allow the reader to close.
                holder.reader = holder.writer.getReader();
                if (holder.dirty) {
                    holder.etag = newEtag();
                    holder.dirty = false;
                }
            }
            
            reader = holder.reader;
        }

        reader.incRef();
        try {
            return callback.callback(reader);
        } finally {
            reader.decRef();
        }
    }

    public <T> T withSearcher(final ViewSignature viewSignature, final boolean staleOk, final SearcherCallback<T> callback)
            throws IOException {
        final Holder holder = getHolder(viewSignature);
        return withReader(viewSignature, staleOk, new ReaderCallback<T>() {

            public T callback(final IndexReader reader) throws IOException {
                return callback.callback(new IndexSearcher(reader), holder.etag);
            }
        });
    }

    public void withWriter(final ViewSignature viewSignature, final WriterCallback callback) throws IOException {
        try {
            final Holder holder = getHolder(viewSignature);
            holder.dirty |= callback.callback(holder.writer);
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
            if (holder.reader != null)
                holder.reader.close();
            holder.writer.rollback();
        }
        // Purge from disk.
        FileUtils.deleteDirectory(new File(baseDir, databaseName));
    }

}
