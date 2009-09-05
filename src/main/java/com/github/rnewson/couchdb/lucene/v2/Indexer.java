package com.github.rnewson.couchdb.lucene.v2;

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

    Indexer(final Database database, final LuceneHolders holders) {
        this.database = database;
        this.holders = holders;
    }

    @Override
    protected void doStart() throws Exception {
        // TODO Auto-generated method stub
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        // TODO Auto-generated method stub
        super.doStop();
    }

}
