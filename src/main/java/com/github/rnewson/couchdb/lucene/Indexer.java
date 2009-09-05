package com.github.rnewson.couchdb.lucene;

import java.util.TimerTask;

import net.sf.json.JSONObject;

/**
 * A task that periodically scans all couchdb databases and makes matching
 * updates to Lucene.
 * 
 * @author rnewson
 * 
 */
public final class Indexer extends TimerTask {

    private Database database;

    public void setDatabase(final Database database) {
        this.database = database;
    }

    @Override
    public void run() {
        try {
            final JSONObject state = database.getDoc("_local", "lucene");
            
        } catch (final Exception e) {

        }
    }
}
