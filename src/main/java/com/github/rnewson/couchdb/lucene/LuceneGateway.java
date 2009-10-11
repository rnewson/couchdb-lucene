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
        private DirtiableIndexWriter writer;
    }

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

    private DirtiableIndexWriter newWriter(final Directory dir) throws IOException {
        final DirtiableIndexWriter result = new DirtiableIndexWriter(dir, Constants.ANALYZER, MaxFieldLength.UNLIMITED);
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

    public <T> T withWriter(final ViewSignature viewSignature, final WriterCallback<T> callback) throws IOException {
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
                } else if (getHolder(viewSignature).writer.isDirty()) {
                    getHolder(viewSignature).etag = newEtag();
                    getHolder(viewSignature).writer.setDirty(false);
                }
            }
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

    /**
     * Track "dirty" status so we only change ETag when documents are added,
     * updated or deleted.
     * 
     * @author robertnewson
     * 
     */
    private class DirtiableIndexWriter extends IndexWriter {

        private boolean isDirty;

        public DirtiableIndexWriter(Directory d, Analyzer a, boolean create, IndexDeletionPolicy deletionPolicy, MaxFieldLength mfl)
                throws CorruptIndexException, LockObtainFailedException, IOException {
            super(d, a, create, deletionPolicy, mfl);
        }

        public DirtiableIndexWriter(Directory d, Analyzer a, boolean create, MaxFieldLength mfl) throws CorruptIndexException,
                LockObtainFailedException, IOException {
            super(d, a, create, mfl);
        }

        public DirtiableIndexWriter(Directory d, Analyzer a, IndexDeletionPolicy deletionPolicy, MaxFieldLength mfl,
                IndexCommit commit) throws CorruptIndexException, LockObtainFailedException, IOException {
            super(d, a, deletionPolicy, mfl, commit);
        }

        public DirtiableIndexWriter(Directory d, Analyzer a, IndexDeletionPolicy deletionPolicy, MaxFieldLength mfl)
                throws CorruptIndexException, LockObtainFailedException, IOException {
            super(d, a, deletionPolicy, mfl);
        }

        public DirtiableIndexWriter(Directory d, Analyzer a, MaxFieldLength mfl) throws CorruptIndexException,
                LockObtainFailedException, IOException {
            super(d, a, mfl);
        }

        public final boolean isDirty() {
            return isDirty;
        }

        protected final void setDirty(final boolean isDirty) {
            this.isDirty = isDirty;
        }

        @Override
        public void addDocument(Document doc, Analyzer analyzer) throws CorruptIndexException, IOException {
            super.addDocument(doc, analyzer);
            setDirty(true);
        }

        @Override
        public void addDocument(Document doc) throws CorruptIndexException, IOException {
            super.addDocument(doc);
            setDirty(true);
        }

        @Override
        public void addIndexes(Directory[] dirs) throws CorruptIndexException, IOException {
            super.addIndexes(dirs);
            setDirty(true);
        }

        @Override
        public void addIndexes(IndexReader[] readers) throws CorruptIndexException, IOException {
            super.addIndexes(readers);
            setDirty(true);
        }

        @Override
        public void addIndexesNoOptimize(Directory[] dirs) throws CorruptIndexException, IOException {
            super.addIndexesNoOptimize(dirs);
            setDirty(true);
        }

        @Override
        public void deleteDocuments(Query query) throws CorruptIndexException, IOException {
            super.deleteDocuments(query);
            setDirty(true);
        }

        @Override
        public void deleteDocuments(Query[] queries) throws CorruptIndexException, IOException {
            super.deleteDocuments(queries);
            setDirty(true);
        }

        @Override
        public void deleteDocuments(Term term) throws CorruptIndexException, IOException {
            super.deleteDocuments(term);
            setDirty(true);
        }

        @Override
        public void deleteDocuments(Term[] terms) throws CorruptIndexException, IOException {
            super.deleteDocuments(terms);
            setDirty(true);
        }

        @Override
        public void updateDocument(Term term, Document doc, Analyzer analyzer) throws CorruptIndexException, IOException {
            super.updateDocument(term, doc, analyzer);
            setDirty(true);
        }

        @Override
        public void updateDocument(Term term, Document doc) throws CorruptIndexException, IOException {
            super.updateDocument(term, doc);
            setDirty(true);
        }

    }

}
