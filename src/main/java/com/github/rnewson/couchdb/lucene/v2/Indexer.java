package com.github.rnewson.couchdb.lucene.v2;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;

import net.sf.json.JSONObject;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
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

    private final Map<String, Thread> activeTasks = new HashMap<String, Thread>();

    Indexer(final Database database, final LuceneHolders holders) {
        this.database = database;
        this.holders = holders;
    }

    @Override
    protected void doStart() throws Exception {
        startTask("databaseScanner", new DatabaseScanner());
    }

    @Override
    protected void doStop() throws Exception {
        for (final Thread thread : activeTasks.values()) {
            thread.interrupt();
            thread.join(500);
        }
    }

    private void startTask(final String taskName, final Runnable runnable) {
        Thread thread;
        synchronized (activeTasks) {
            thread = activeTasks.get(taskName);
            // Is task already running?
            if (thread != null && thread.isAlive())
                return;
            thread = new Thread(runnable);
            thread.setDaemon(true);
            activeTasks.put(taskName, thread);
        }
        // Start it.
        if (!thread.isAlive())
            thread.start();
    }

    /**
     * Periodically queries _all_dbs looking for newly created or updated
     * databases.
     */
    private class DatabaseScanner implements Runnable {

        @Override
        public void run() {
            try {
                while (Indexer.this.isRunning()) {
                    final String[] all_dbs = database.getAllDatabases();
                    // TODO Delete anything locally that doesn't exist in couch.

                    // Ensure a tracking task exists for each database.
                    for (final String db : all_dbs) {
                        startTask(db, new DatabaseTracker(db));
                    }

                    SECONDS.sleep(15);
                }
            } catch (final ConnectException e) {
                // Silently ignore couchdb being down.
            } catch (final InterruptedException e) {
                return;
            } catch (final Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }

    /**
     * Track all changes to database.
     * 
     * @author rnewson
     * 
     */
    private class DatabaseTracker implements Runnable {

        private final String db;

        public DatabaseTracker(final String db) {
            this.db = db;
        }

        @Override
        public void run() {
            final HttpClient client = new DefaultHttpClient();
            int since = 0;

            final ResponseHandler<Integer> responseHandler = new ResponseHandler<Integer>() {

                @Override
                public Integer handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                    int last_seq = 0;
                    final HttpEntity entity = response.getEntity();
                    final BufferedReader reader = new BufferedReader(
                            new InputStreamReader(entity.getContent(), "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                        final JSONObject obj = JSONObject.fromObject(line);
                        if (obj.has("last_seq")) {
                            last_seq = obj.getInt("last_seq");
                            break;
                        }
                       // final JSONObject doc = database.getDoc(db, obj.getString("id"));
                       // System.out.println(doc);
                    }
                    return last_seq;
                }
            };

            try {
                while (Indexer.this.isRunning()) {
                    final HttpGet get = new HttpGet("http://localhost:5984/" + db + "/_changes?feed=continuous&since="
                            + since);
                    since = client.execute(get, responseHandler);
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

}
