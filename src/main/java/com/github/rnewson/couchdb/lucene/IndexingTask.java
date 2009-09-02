package com.github.rnewson.couchdb.lucene;

import java.util.TimerTask;

/**
 * A task that periodically scans all couchdb databases and makes matching
 * updates to Lucene.
 * 
 * @author rnewson
 * 
 */
public final class IndexingTask extends TimerTask {

    private Database database;

    public void setDatabase(final Database database) {
        this.database = database;
    }

    @Override
    public void run() {

    }

}
