package com.github.rnewson.couchdb.lucene;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import com.github.rnewson.couchdb.lucene.util.Constants;

public final class Lucene {

    private final File root;
    private final CouchDbRegistry registry;
    private final IdempotentExecutor<IndexKey> executor = new IdempotentExecutor<IndexKey>();
    private final Map<IndexKey, Tuple> map = new HashMap<IndexKey, Tuple>();

    private static class Tuple {
        private String version;
        private boolean dirty;
        private final IndexWriter writer;
        private IndexReader reader;

        public Tuple(final IndexWriter writer) {
            this.writer = writer;
        }

        public void close() throws IOException {
            if (reader != null)
                reader.close();
            if (writer != null)
                writer.close();
        }

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
        executor.submit(indexKey, new ViewIndexer(this, registry, indexKey));
    }

    public void withReader(final IndexKey key, final boolean staleOk, final ReaderCallback callback) throws IOException {
        final Tuple tuple;
        synchronized (map) {
            tuple = map.get(key);
        }
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
        final Tuple tuple;
        synchronized (map) {
            tuple = map.get(key);
        }

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

    public void createWriter(final IndexKey key, final UUID uuid, final String function) throws IOException {
        final String digest = digest(function);
        final File dir = new File(new File(root, uuid.toString()), digest);
        dir.mkdirs();

        synchronized (map) {
            Tuple tuple = map.remove(key);
            if (tuple != null) {
                tuple.close();
            }
            final Directory d = FSDirectory.open(dir);
            tuple = new Tuple(newWriter(d));
            map.put(key, tuple);
        }
    }

    public void close() {
        executor.shutdownNow();
    }

    public static String digest(final String function) {
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(function.replaceAll("\\s+", "").getBytes("UTF-8"));
            return new BigInteger(1, md.digest()).toString(Character.MAX_RADIX);
        } catch (final NoSuchAlgorithmException e) {
            throw new Error("MD5 support missing.");
        } catch (final UnsupportedEncodingException e) {
            throw new Error("UTF-8 support missing.");
        }
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

}
