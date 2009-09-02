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

import static com.github.rnewson.couchdb.lucene.Utils.docQuery;
import static com.github.rnewson.couchdb.lucene.Utils.token;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.http.HttpException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LogByteSizeMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.CheckIndex.Status;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import com.github.rnewson.couchdb.lucene.util.Analyzers;

public final class Index {

    private static final Database DB = new Database(Config.DB_URL);

    static class Indexer implements Runnable {

        private long staleAt = now();

        private long freshAt = staleAt;

        private final Directory dir;

        public Indexer(final Directory dir) {
            this.dir = dir;
        }

        public synchronized boolean isStale() {
            return staleAt >= freshAt;
        }

        public synchronized void setStale() {
            staleAt = now() + leniency();
        }

        public synchronized void setFresh() {
            freshAt = now() + leniency();
        }

        private long leniency() {
            return MILLISECONDS.toNanos(Config.COMMIT_MIN);
        }

        private long now() {
            return System.nanoTime();
        }

        public void run() {
            while (true) {
                if (isStale()) {
                    try {
                        updateIndex();
                    } catch (final Throwable t) {
                        Utils.LOG.warn("Exception while updating index.", t);
                    }
                } else {
                    sleep();
                }
            }
        }

        private void sleep() {
            try {
                Thread.sleep(Config.COMMIT_MIN);
            } catch (final InterruptedException e) {
                Utils.LOG.fatal("Interrupted while sleeping, indexer is exiting.", e);
            }
        }

        private IndexWriter newWriter() throws IOException {
            final IndexWriter result = new IndexWriter(Config.INDEX_DIR, Config.ANALYZER, MaxFieldLength.UNLIMITED);

            // Customize merge policy.
            final LogByteSizeMergePolicy mp = new LogByteSizeMergePolicy(result);
            mp.setMergeFactor(5);
            mp.setMaxMergeMB(1000);
            mp.setUseCompoundFile(false);
            result.setMergePolicy(mp);

            result.setRAMBufferSizeMB(Config.RAM_BUF);

            if (Config.LUCENE_DEBUG) {
                result.setInfoStream(System.err);
            }

            return result;
        }

        private synchronized void updateIndex() throws IOException {
            if (IndexWriter.isLocked(dir)) {
                Utils.LOG.warn("Forcibly unlocking locked index at startup.");
                IndexWriter.unlock(dir);
            }

            final String[] dbnames = DB.getAllDatabases();
            Arrays.sort(dbnames);

            boolean expunge = false;
            final IndexWriter writer = newWriter();
            final Progress progress = new Progress();
            try {
                final IndexReader reader = IndexReader.open(dir);
                try {
                    // Load status.
                    progress.load(reader);

                    // Remove documents from deleted databases.
                    final TermEnum terms = reader.terms(new Term(Config.DB, ""));
                    try {
                        do {
                            final Term term = terms.term();
                            if (term == null || Config.DB.equals(term.field()) == false)
                                break;
                            if (Arrays.binarySearch(dbnames, term.text()) < 0) {
                                Utils.LOG.info("Database '" + term.text()
                                        + "' has been deleted, removing all documents from index.");
                                deleteDatabase(term.text(), progress, writer);
                                expunge = true;
                            }
                        } while (terms.next());
                    } finally {
                        terms.close();
                    }
                } finally {
                    reader.close();
                }

                // Update all extant databases.
                for (final String dbname : dbnames) {
                    // Iterate through all views in all design documents.
                    final JSONObject designDocs = DB.getAllDocs(dbname, "_design", "_design0");

                    // Get rows.
                    final JSONArray arr = designDocs.getJSONArray("rows");

                    boolean delete_all = true;
                    // For each row, extract all fulltext view functions.
                    for (int i = 0; i < arr.size(); i++) {
                        final JSONObject doc = arr.getJSONObject(i).getJSONObject("doc");
                        final JSONObject fulltext = doc.getJSONObject("fulltext");
                        if (fulltext != null) {
                            delete_all = false;
                            for (final Object obj : fulltext.keySet()) {
                                final String key = (String) obj;
                                final String sig = Utils.digest(fulltext.getString(key));
                                final String defaults = fulltext.getJSONObject(key).optString("defaults", "{}");
                                final Analyzer analyzer = Analyzers.getAnalyzer(fulltext.getJSONObject(key)
                                        .optString("analyzer", "standard"));

                                String fun = fulltext.getJSONObject((String) key).getString("index");
                                fun = fun.replaceAll("^\"*", "");
                                fun = fun.replaceAll("\"*$", "");

                                final String viewname = String.format("%s/%s/%s", dbname, doc.getString(Config.ID)
                                        .replaceFirst("_design/", ""), key);

                                final Rhino rhino = new Rhino(dbname, defaults, fun);
                                try {
                                    updateDatabase(writer, analyzer, sig, dbname, viewname, progress, rhino);
                                } finally {
                                    rhino.close();
                                }
                            }
                        }
                    }

                    /*
                     * If there are no fulltext attributes in any design
                     * document, ensure that nothing is indexed for this
                     * database.
                     */
                    if (delete_all) {
                        deleteDatabase(dbname, progress, writer);
                    }
                }
            } catch (final Exception e) {
                Utils.LOG.error("Error updating index.", e);
            } finally {
                progress.save(writer);
                if (expunge) {
                    writer.expungeDeletes();
                }
                writer.close();

                final IndexReader reader = IndexReader.open(dir);
                try {
                    Utils.LOG.info("Committed changes to index (" + reader.numDocs() + " documents in index, "
                            + reader.numDeletedDocs() + " deletes).");
                } finally {
                    reader.close();
                }
                setFresh();
            }
        }

        private void updateDatabase(final IndexWriter writer, final Analyzer analyzer, final String new_sig,
                final String dbname, final String viewname, final Progress progress, final Rhino rhino)
                throws HttpException, IOException {
            assert rhino != null;

            final long start = now();
            final long target_seq = DB.getInfo(dbname).getLong("update_seq");

            final String cur_sig = progress.getSignature(viewname);

            // Reindex the database if sequence is 0 or signature changed.
            if (progress.getSeq(viewname) == 0 || cur_sig.equals(new_sig) == false) {
                Utils.LOG.info("Indexing " + viewname + " from scratch.");
                deleteView(viewname, progress, writer);
                progress.update(viewname, new_sig, 0);
            }

            long update_seq = progress.getSeq(viewname);
            while (update_seq < target_seq) {
                final JSONObject obj = DB.getAllDocsBySeq(dbname, update_seq, Config.BATCH_SIZE);

                if (!obj.has("rows")) {
                    Utils.LOG.warn("no rows found (" + obj + ").");
                }

                // Process all rows
                final JSONArray rows = obj.getJSONArray("rows");
                for (int i = 0, max = rows.size(); i < max; i++) {
                    final JSONObject row = rows.getJSONObject(i);
                    final JSONObject value = row.optJSONObject("value");
                    final JSONObject doc = row.optJSONObject("doc");
                    final String docid = row.getString("id");

                    // New or updated document.
                    if (doc != null && !docid.startsWith("_design")) {
                        writer.deleteDocuments(docQuery(viewname, row.getString("id")));
                        final Document[] docs = rhino.map(docid, doc.toString());

                        for (int j = 0; j < docs.length; j++) {
                            docs[j].add(token(Config.DB, dbname, false));
                            docs[j].add(token(Config.VIEW, viewname, false));
                            docs[j].add(token(Config.ID, docid, true));

                            if (Utils.LOG.isTraceEnabled()) {
                                Utils.LOG.trace("Adding " + docs[j]);
                            }
                            writer.addDocument(docs[j], analyzer);
                        }
                    }

                    // Deleted document.
                    if (value != null && value.optBoolean("deleted")) {
                        writer.deleteDocuments(docQuery(viewname, row.getString("id")));
                    }

                    update_seq = row.getLong("key");
                }
            }

            progress.update(viewname, new_sig, update_seq);

            final long duration = now() - start;
            Utils.LOG.debug(String.format("%s: index is now at update_seq %,d (took %s).", viewname, update_seq,
                    DurationFormatUtils.formatDurationHMS(NANOSECONDS.toMillis(duration))));
        }

        private void deleteView(final String viewname, final Progress progress, final IndexWriter writer)
                throws IOException {
            writer.deleteDocuments(new Term(Config.VIEW, viewname));
            progress.removeView(viewname);
        }

        private void deleteDatabase(final String dbname, final Progress progress, final IndexWriter writer)
                throws IOException {
            writer.deleteDocuments(new Term(Config.DB, dbname));
            progress.removeDatabase(dbname);
        }

    }

    public static void main(final String[] args) throws Exception {
        final File dir = new File(Config.INDEX_DIR);

        // Create index directory if missing.
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                Utils.LOG.fatal("Unable to create index dir " + dir.getAbsolutePath());
                System.exit(1);
            }
        }

        // Verify index directory is writable.
        final File canWrite = new File(dir, ".writable");
        canWrite.delete(); // delete stale copy.
        try {
            canWrite.createNewFile();
        } catch (final IOException e) {
            Utils.LOG.fatal(dir.getAbsolutePath() + " is not writable.");
            System.exit(1);
        } finally {
            canWrite.delete();
        }

        // Check index prior to startup if it exists.
        final Directory d = FSDirectory.getDirectory(dir);
        if (IndexReader.indexExists(d)) {
            final CheckIndex check = new CheckIndex(d);
            final Status status = check.checkIndex();
            if (status.clean)
                Utils.LOG.info("Index is clean.");
            else
                Utils.LOG.warn("Index is not clean.");
        }

        Utils.LOG.info("indexer started.");
        final Indexer indexer = new Indexer(d);
        indexer.updateIndex();
        final Thread thread = new Thread(indexer, "index");
        thread.setDaemon(true);
        thread.start();

        final Scanner scanner = new Scanner(System.in, "UTF-8");
        while (scanner.hasNextLine()) {
            final String line = scanner.nextLine();
            final JSONObject obj = JSONObject.fromObject(line);
            if (obj.has("type") && obj.has("db")) {
                indexer.setStale();
            }
        }
        Utils.LOG.info("indexer stopped.");
    }
}
