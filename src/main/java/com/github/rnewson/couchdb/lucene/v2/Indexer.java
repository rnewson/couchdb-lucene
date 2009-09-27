package com.github.rnewson.couchdb.lucene.v2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.log4j.Logger;
import org.apache.lucene.index.Term;
import org.mortbay.component.AbstractLifeCycle;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;

/**
 * Pull data from couchdb into Lucene indexes.
 * 
 * @author robertnewson
 */
public final class Indexer extends AbstractLifeCycle {

    private final Logger logger = Logger.getLogger(Indexer.class);

    private State state;

    private final Set<String> activeTasks = new HashSet<String>();

    private ScheduledExecutorService scheduler;

    public Indexer(final State state) {
        this.state = state;
    }

    @Override
    protected void doStart() throws Exception {
        scheduler = Executors.newScheduledThreadPool(5);
        scheduler.scheduleWithFixedDelay(new CouchPoller(), 0, 1, TimeUnit.MINUTES);
    }

    @Override
    protected void doStop() throws Exception {
        scheduler.shutdown();
        scheduler.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    private class CouchPoller implements Runnable {

        @Override
        public void run() {
            try {
                final String[] databases = state.couch.getAllDatabases();
                synchronized (activeTasks) {
                    for (final String databaseName : databases) {
                        if (!activeTasks.contains(databaseName)) {
                            logger.debug("Tracking " + databaseName);
                            activeTasks.add(databaseName);
                            scheduler.execute(new DatabasePuller(databaseName));
                        }
                    }
                }
            } catch (final HttpException e) {
                // Ignore.
            } catch (final IOException e) {
                // Ignore.
            }
        }
    }

    private class DatabasePuller implements Runnable {

        private final String databaseName;

        private long since;

        private Context context;

        private ScriptableObject scope;

        private Function main;

        private final Map<String, Function> functions = new HashMap<String, Function>();

        public DatabasePuller(final String databaseName) {
            this.databaseName = databaseName;
        }

        @Override
        public void run() {
            try {
                enterContext();
                mapAllDesignDocuments();
                while (isRunning()) {
                    updateIndexes();
                }
            } catch (final Exception e) {
                logger.warn("Tracking for database " + databaseName + " interrupted by exception.", e);
            } finally {
                leaveContext();
                untrack();
            }
        }

        private void enterContext() throws Exception {
            context = ContextFactory.getGlobal().enterContext();
            // Security restrictions
            context.setClassShutter(new RestrictiveClassShutter());
            // Setup.
            scope = context.initStandardObjects();
            // Allow custom document helper class.
            ScriptableObject.defineClass(scope, RhinoDocument.class);
            // Load JSON parser.
            context.evaluateString(scope, loadResource("json2.js"), "json2", 0, null);
            // Define outer function.
            main = context.compileFunction(scope, "function(json, func){return func(JSON.parse(json));}", "main", 0, null);
        }

        private String loadResource(final String name) throws IOException {
            final InputStream in = Rhino.class.getClassLoader().getResourceAsStream(name);
            try {
                return IOUtils.toString(in, "UTF-8");
            } finally {
                in.close();
            }
        }

        private void mapAllDesignDocuments() throws IOException {
            final JSONArray designDocuments = state.couch.getAllDesignDocuments(databaseName);
            for (int i = 0; i < designDocuments.size(); i++) {
                mapDesignDocument(designDocuments.getJSONObject(i).getJSONObject("doc"));
            }
        }

        private void mapDesignDocument(final JSONObject designDocument) {
            final String designDocumentName = designDocument.getString(Constants.ID).substring(8);
            final JSONObject fulltext = designDocument.getJSONObject("fulltext");
            if (fulltext != null) {
                for (final Object obj : fulltext.keySet()) {
                    final String viewName = (String) obj;
                    final JSONObject viewValue = fulltext.getJSONObject(viewName);
                    final String viewFunction = viewValue.getString("index");
                    functions.put(viewName, context.compileFunction(scope, viewFunction, viewName, 0, null));
                    state.locator.update(databaseName, designDocumentName, viewName, fulltext.toString());
                }
            }
        }

        private void updateIndexes() throws IOException {
            // System.err.println(state.locator.lookupAll(databaseName));
            final String url = state.couch.url(String.format("%s/_changes?feed=continuous&since=%d&include_docs=true",
                    databaseName, since));
            state.httpClient.execute(new HttpGet(url), new ChangesResponseHandler());
        }

        private void untrack() {
            synchronized (activeTasks) {
                activeTasks.remove(databaseName);
            }
            logger.debug("Untracking " + databaseName);
        }

        private void leaveContext() {
            Context.exit();
        }

        private final class RestrictiveClassShutter implements ClassShutter {
            @Override
            public boolean visibleToScripts(final String fullClassName) {
                return false;
            }
        }

        private final class ChangesResponseHandler implements ResponseHandler<Void> {
            @Override
            public Void handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                final HttpEntity entity = response.getEntity();
                final BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    final JSONObject json = JSONObject.fromObject(line);
                    // End of feed.
                    if (json.has("last_seq"))
                        break;

                    final String id = json.getString("id");
                    final Term docTerm = new Term(Constants.ID, id);
                    final JSONObject doc = json.getJSONObject("doc");

                    // New, updated or deleted document.
                    if (id.startsWith("_design")) {
                        if (logger.isTraceEnabled())
                            logger.trace(id + ": design document updated.");
                        mapDesignDocument(doc);
                    } else if (json.optBoolean("deleted")) {
                        if (logger.isTraceEnabled())
                            logger.trace(id + ": document deleted.");
                        //writer.deleteDocuments(docTerm);
                    } else {
                        // New or updated document.
                        if (logger.isTraceEnabled())
                            logger.trace(id + ": new/updated document.");

                        for (final Function function : functions.values()) {
                            final Object result = main.call(context, scope, null, new Object[] { doc, function });
                            System.err.println(result);
                        }
                    }

                    // Remember progress.
                    since = json.getLong("seq");
                }
                return null;
            }
        }

    }

}