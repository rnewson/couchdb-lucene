package com.github.rnewson.couchdb.lucene.v2;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.mortbay.component.AbstractLifeCycle;

/**
 * Pull changes from couchdb into Lucene indexes.
 * 
 * @author rnewson
 * 
 */
final class Indexer extends AbstractLifeCycle {

    private final Database database;

    private final LuceneHolders holders;

    private ScheduledExecutorService scheduler;

    Indexer(final Database database, final LuceneHolders holders) {
        this.database = database;
        this.holders = holders;
    }

    @Override
    protected void doStart() throws Exception {
        scheduler = Executors.newScheduledThreadPool(1);
    }

    @Override
    protected void doStop() throws Exception {
        scheduler.shutdown();
        scheduler.awaitTermination(30, SECONDS);
    }

    private class IndexerRunnable implements Runnable {

        @Override
        public void run() {

        }

    }

}
