package com.github.rnewson.couchdb.lucene;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;

import com.github.rnewson.couchdb.lucene.util.Constants;

public final class Lucene {

    private final File root;
    private final CouchDbRegistry registry;
    private final IdempotentExecutor<TaskKey> executor = new IdempotentExecutor<TaskKey>();
    private final Map<IndexKey, Tuple> map = Collections.synchronizedMap(new HashMap<IndexKey, Tuple>());

    private static class Tuple {
        private String version;
        private boolean dirty;
        private IndexWriter writer;
        private IndexReader reader;
    }

    public interface ReaderCallback {
        public void callback(final IndexReader reader) throws IOException;

        public void onMissing() throws IOException;
    }

    public interface SearcherCallback {
        public void callback(final IndexSearcher searcher, final String version) throws IOException;

        public void onMissing() throws IOException;
    }

    public interface WriterCallback {
        /**
         * @return if index was modified (add, update, delete)
         */
        public boolean callback(final IndexWriter writer) throws IOException;

        public void onMissing() throws IOException;
    }

    public Lucene(final File root, final CouchDbRegistry registry) {
        this.root = root;
        this.registry = registry;
    }

    public void startIndexing(final IndexKey indexKey) {
        final String url = registry.url(indexKey.getHostKey(), indexKey.getDatabaseName());
        executor.submit(new TaskKey(indexKey), new DatabaseIndexer(url));
    }

    public void withReader(final IndexKey key, final boolean staleOk, final ReaderCallback callback) throws IOException {
        final Tuple tuple = map.get(key);
        if (tuple == null) {
            callback.onMissing();
            return;
        }

        final IndexReader reader;

        synchronized (tuple) {
            if (tuple.reader == null) {
                tuple.reader = tuple.writer.getReader();
                tuple.version = newVersion();
                tuple.reader.incRef(); // keep the reader open.
            }

            if (!staleOk) {
                tuple.reader.decRef(); // allow the reader to close.
                tuple.reader = tuple.writer.getReader();
                if (tuple.dirty) {
                    tuple.version = newVersion();
                    tuple.dirty = false;
                }
            }

            reader = tuple.reader;
        }

        reader.incRef();
        try {
            callback.callback(reader);
        } finally {
            reader.decRef();
        }
    }

    public void withSearcher(final IndexKey key, final boolean staleOk, final SearcherCallback callback) throws IOException {
        withReader(key, staleOk, new ReaderCallback() {

            public void callback(final IndexReader reader) throws IOException {
                callback.callback(new IndexSearcher(reader), map.get(key).version);
            }

            public void onMissing() throws IOException {
                callback.onMissing();
            }
        });
    }

    public void withWriter(final IndexKey key, final WriterCallback callback) throws IOException {
        final Tuple tuple = map.get(key);
        if (tuple == null) {
            callback.onMissing();
            return;
        }

        try {
            final boolean dirty = callback.callback(tuple.writer);
            synchronized (tuple) {
                tuple.dirty = dirty;
            }
        } catch (final OutOfMemoryError e) {
            map.remove(key).writer.rollback();
            throw e;
        }
    }

    public void close() {
        executor.shutdownNow();
    }

    private IndexWriter newWriter(final Directory dir) throws IOException {
        final IndexWriter result = new IndexWriter(dir, Constants.ANALYZER, MaxFieldLength.UNLIMITED);
        result.setMergeFactor(5);
        result.setUseCompoundFile(false);
        return result;
    }

    private String newVersion() {
        return Long.toHexString(System.nanoTime());
    }

    private static class TaskKey {

        private final String hostKey;
        private final String databaseName;

        public TaskKey(final IndexKey key) {
            this.hostKey = key.getHostKey();
            this.databaseName = key.getDatabaseName();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + databaseName.hashCode();
            result = prime * result + hostKey.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            TaskKey other = (TaskKey) obj;
            if (!databaseName.equals(other.databaseName))
                return false;
            if (!hostKey.equals(other.hostKey))
                return false;
            return true;
        }

    }

}
