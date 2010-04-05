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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.sf.json.JSONObject;

import org.apache.http.client.HttpClient;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.Database;
import com.github.rnewson.couchdb.lucene.couchdb.DesignDocument;
import com.github.rnewson.couchdb.lucene.couchdb.View;
import com.github.rnewson.couchdb.lucene.util.Constants;
import com.github.rnewson.couchdb.lucene.util.IndexPath;
@Deprecated
public final class Lucene {

    private final File root;
    private final HttpClient client = HttpClientFactory.getInstance();
    private final IdempotentExecutor<ViewKey, ViewIndexer> executor = new IdempotentExecutor<ViewKey, ViewIndexer>();
    private final Map<IndexPath, ViewKey> keys = new HashMap<IndexPath, ViewKey>();
    private final Map<ViewKey, Tuple> tuples = new HashMap<ViewKey, Tuple>();
    
    private static class Tuple {
        private String version;
        private boolean dirty;
        private IndexWriter writer;
        private IndexReader reader;
        private QueryParser parser;

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
        public void callback(final IndexSearcher searcher, final QueryParser parser, final String version) throws IOException;

        public void onMissing() throws IOException;
    }

    public interface WriterCallback {
        /**
         * @return if index was modified (add, update, delete)
         */
        public boolean callback(final IndexWriter writer) throws IOException;

        public void onMissing() throws IOException;
    }

    public Lucene(final File root) {
        this.root = root;
    }

    public ViewIndexer startIndexing(final IndexPath path, final boolean staleOk) throws IOException {
    	final ViewKey viewKey = getViewKey(path);    	
        final ViewIndexer result = executor.submit(viewKey, new ViewIndexer(this, path, staleOk));
        result.awaitInitialIndexing();
        return result;
    }

    public void withReader(final IndexPath path, final boolean staleOk, final ReaderCallback callback) throws IOException {
        final Tuple tuple = getTuple(path);

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

    public void withSearcher(final IndexPath path, final boolean staleOk, final SearcherCallback callback) throws IOException {
        withReader(path, staleOk, new ReaderCallback() {

            public void callback(final IndexReader reader) throws IOException {
            	final Tuple tuple = getTuple(path);
                callback.callback(new IndexSearcher(reader), tuple.parser, tuple.version);
            }

            public void onMissing() throws IOException {
                callback.onMissing();
            }
        });
    }

    public void withWriter(final IndexPath path, final WriterCallback callback) throws IOException {
        final Tuple tuple = getTuple(path);

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
            removeTuple(path).writer.rollback();
            throw e;
        }
    }

	public void createWriter(final IndexPath path, final UUID uuid,
			final View view) throws IOException {
		final File dir = new File(getUuidDir(uuid), view.getDigest());
		dir.mkdirs();

		Tuple tuple = removeTuple(path);
		if (tuple != null) {
			tuple.close();
		}
		final Directory d = FSDirectory.open(dir);
		tuple = new Tuple();
		tuple.writer = newWriter(d);
		tuple.parser = new CustomQueryParser(Constants.VERSION,
				Constants.DEFAULT_FIELD, view.getAnalyzer());

		putTuple(path, tuple);
	}

    public File getRootDir() {
        return root;
    }

    public File getUuidDir(final UUID uuid) {
        return new File(getRootDir(), uuid.toString());
    }

    public void close() {
        executor.shutdownNow();
    }

    public static String digest(final JSONObject view) {
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(toBytes(view.optString("analyzer")));
            md.update(toBytes(view.optString("defaults")));
            md.update(toBytes(view.optString("index")));
            return new BigInteger(1, md.digest()).toString(Character.MAX_RADIX);
        } catch (final NoSuchAlgorithmException e) {
            throw new Error("MD5 support missing.");
        }
    }

    private static byte[] toBytes(final String str) {
        if (str == null) {
            return new byte[0];
        }
        try {
            return str.getBytes("UTF-8");
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

	private Tuple getTuple(final IndexPath path) throws IOException {
		final ViewKey viewKey = getViewKey(path);
		synchronized (tuples) {
			return tuples.get(viewKey);
		}
	}
	
	private Tuple removeTuple(final IndexPath path) throws IOException {
		final ViewKey viewKey = getViewKey(path);
		synchronized (tuples) {
			return tuples.remove(viewKey);
		}
	}
	
	private Tuple putTuple(final IndexPath path, final Tuple tuple) throws IOException {
		final ViewKey viewKey = getViewKey(path);
		synchronized (tuples) {
			return tuples.put(viewKey, tuple);
		}
	}

	private ViewKey getViewKey(final IndexPath path) throws IOException {
		ViewKey result;
		synchronized (keys) {
			result = keys.get(path);
		}
		if (result != null) {
			return result;
		}
		
        final Couch couch = Couch.getInstance(client, path.getUrl());
        final Database database = couch.getDatabase(path.getDatabase());		
		final DesignDocument ddoc = database.getDesignDocument(path.getDesignDocumentName());
        final View view = ddoc.getView(path.getViewName());
        result = new ViewKey(newOrCurrentUuid(database), view);
        
        synchronized (keys) {
        	keys.put(path, result);
        }
        
        return result;
	}
	
    private UUID newOrCurrentUuid(final Database database) throws IOException {
        UUID result = database.getUuid();
        if (result == null) {
                database.createUuid();
                result = database.getUuid();
        }
        return result;
    }

}
