package com.github.rnewson.couchdb.lucene.v2;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.http.client.HttpResponseException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.mortbay.component.AbstractLifeCycle;

import com.github.rnewson.couchdb.lucene.v2.LuceneGateway.WriterCallback;

/**
 * Pull changes from couchdb into Lucene indexes.
 * 
 * @author rnewson
 * 
 */
final class Indexer extends AbstractLifeCycle {

    private static final int BATCH_SIZE = 1000;

    private final Database database;

    private final LuceneGateway holders;

    private Timer timer;

    Indexer(final Database database, final LuceneGateway holders) {
        this.database = database;
        this.holders = holders;
    }

    @Override
    protected void doStart() throws Exception {
        timer = new Timer(true);
        timer.schedule(new DatabaseSyncTask(), 5000, 5000);
    }

    @Override
    protected void doStop() throws Exception {
        timer.cancel();
    }

    private class DatabaseSyncTask extends TimerTask {

        @Override
        public void run() {
            try {
                for (final String databaseName : database.getAllDatabases()) {
                    updateDatabase(databaseName);
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }

        private void updateDatabase(final String databaseName) throws IOException {
            final JSONObject designDocuments = database.getAllDocs(databaseName, "_design", "_design0");
            final JSONArray arr = designDocuments.getJSONArray("rows");
            // For each design document;
            for (int i = 0; i < arr.size(); i++) {
                final JSONObject designDocument = arr.getJSONObject(i).getJSONObject("doc");
                final JSONObject fulltext = designDocument.getJSONObject("fulltext");
                if (fulltext == null) {
                    // TODO delete index if we have one.
                    continue;
                }
                // For each fulltext view;
                for (final Object obj : fulltext.keySet()) {
                    final String viewName = Utils.viewname(databaseName, designDocument.getString("_id"), (String) obj);
                    final Rhino rhino = new Rhino(databaseName, defaults, 
                            fun);                    
                    final String defaults = fulltext.getJSONObject(key).optString("defaults", "{}");
                    final Analyzer analyzer = Analyzers.getAnalyzer(fulltext.getJSONObject(key)
                            .optString("analyzer", "standard"));

                    final long startSequence = getState(databaseName);
                    final long newSequence = holders.withWriter(viewName, new UpdateDatabaseCallback(databaseName,
                            startSequence));
                    if (newSequence != startSequence) {
                        // setState(databaseName, newSequence);
                    }
                }
            }
        }

    }

    private class UpdateDatabaseCallback implements WriterCallback<Long> {

        private final long startSequence;
        private final String databaseName;

        public UpdateDatabaseCallback(final String databaseName, final long startSequence) {
            this.databaseName = databaseName;
            this.startSequence = startSequence;
        }

        @Override
        public Long callback(final IndexWriter writer) throws IOException {
            final JSONObject info = database.getInfo(databaseName);
            final long endSequence = info.getLong("update_seq");

            if (endSequence == startSequence) {
                // We're up to date.
                return startSequence;
            }

            if (endSequence < startSequence) {
                System.out.println("REGRESSION!");
            }

            long currentSequence = startSequence;
            while (currentSequence < endSequence) {
                final JSONObject allDocsBySeq = database.getAllDocsBySeq(databaseName, currentSequence, BATCH_SIZE);
                final JSONArray rows = allDocsBySeq.getJSONArray("rows");
                for (int i = 0, max = rows.size(); i < max; i++) {
                    final JSONObject row = rows.getJSONObject(i);
                    final JSONObject value = row.optJSONObject("value");
                    final JSONObject doc = row.optJSONObject("doc");
                    final String docid = row.getString("id");
                    currentSequence = row.getLong("key");

                    // Do not index design documents.
                    if (docid.startsWith("_design/")) {
                        continue;
                    }

                    System.out.println(value);

                    final Term docTerm = new Term(Constants.ID, docid);
                    if (value.optBoolean("deleted")) {
                        writer.deleteDocuments(docTerm);
                    } else {
                        // TODO optimize GC by reusing Document, Field, NumericField objects.
                        final Document ldoc = new Document();
                        
                        // Add mandatory fields.
                        ldoc.add(new Field(Constants.ID, docid, Store.YES, Index.ANALYZED));
                        ldoc.add(new NumericField(Constants.SEQ, Constants.SEQ_PRECISION).setLongValue(currentSequence));
                        
                        writer.updateDocument(docTerm, ldoc);
                    }
                }

                writer.commit();
            }

            return endSequence;
        }

    }

}
