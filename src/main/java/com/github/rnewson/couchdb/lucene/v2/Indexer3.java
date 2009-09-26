package com.github.rnewson.couchdb.lucene.v2;

import java.io.IOException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.mortbay.component.AbstractLifeCycle;

/**
 * Pull data from couchdb into Lucene indexes.
 * 
 * @author robertnewson
 */
public final class Indexer3 extends AbstractLifeCycle {

    private State state;

    private Thread indexerThread;

    public Indexer3(final State state) {
        this.state = state;
    }

    @Override
    protected void doStart() throws Exception {
        bootstrap();
        startThread();
    }

    @Override
    protected void doStop() throws Exception {
        stopIndexer();
        closeIndexes();
    }

    private void closeIndexes() throws IOException {
        state.gateway.close();
    }

    private void stopIndexer() throws InterruptedException {
        indexerThread.interrupt();
        indexerThread.wait(5000);
    }

    private void startThread() {
        indexerThread = new Thread() {

            @Override
            public void run() {
                while (isRunning()) {
                    updateIndex();
                }
            }

        };
    }

    private void bootstrap() throws Exception {
        // Map all current views to their indexes.
        for (final String databaseName : state.couch.getAllDatabases()) {
            final JSONArray designDocuments = state.couch.getAllDesignDocuments(databaseName);
            for (int i = 0; i < designDocuments.size(); i++) {
                final JSONObject designDocument = designDocuments.getJSONObject(i).getJSONObject("doc");
                final String designDocumentName = designDocument.getString(Constants.ID).substring(8); // strip
                                                                                                       // "_design/"
                                                                                                       // prefix.
                final JSONObject fulltext = designDocument.getJSONObject("fulltext");
                if (fulltext != null) {
                    for (final Object obj : fulltext.keySet()) {
                        final String viewName = (String) obj;
                        state.locator.update(databaseName, designDocumentName, viewName, fulltext.getString(viewName));
                    }
                }
            }
        }
    }

    private void updateIndex() {

    }

}