package com.github.rnewson.couchdb.lucene;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.sf.json.JSONObject;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.apache.tika.io.IOUtils;

/**
 * TODO; ignore ddoc changes lower than update_seq of current ddocs.
 * 
 * @author robertnewson
 * 
 */
public class Spike {

    private static final Logger LOG = Logger.getLogger(Spike.class);

    private static final Map<String, UUID> dbs = new HashMap<String, UUID>();
    private static final Map<UUID, IndexWriter> writers = new HashMap<UUID, IndexWriter>();

    public static void main(String[] args) throws Exception {
        final HttpClient client = new DefaultHttpClient();
        long progress = 0;
        UUID uuid = null;
        long sleep = 1;

        while (true) {
            try {
                HttpGet get = new HttpGet("http://localhost:5984/db1/_local/lucene");
                uuid = client.execute(get, new UUIDHandler());
                sleep = 1;

                if (uuid == null) {
                    // Make a new UUID for this db.
                    final JSONObject json = new JSONObject();
                    final UUID newUUID = UUID.randomUUID();
                    json.put("uuid", newUUID.toString());
                    final HttpPut put = new HttpPut("http://localhost:5984/db1/_local/lucene");
                    put.setEntity(new StringEntity(json.toString()));
                    client.execute(put, new BasicResponseHandler());
                    // if 201 then
                    uuid = newUUID;
                    continue;
                }

                dbs.put("db1", uuid);

                LOG.info(dbs);

                if (!writers.containsKey(uuid)) {
                    final Directory dir = new RAMDirectory();
                    final IndexWriter writer = new IndexWriter(dir, new StandardAnalyzer(Version.LUCENE_CURRENT),
                            MaxFieldLength.UNLIMITED);
                    writers.put(uuid, writer);
                }

                get = new HttpGet("http://localhost:5984/db1/_changes?feed=continuous&heartbeat=5000&since=" + progress);
                progress = client.execute(get, new ProgressHandler(writers.get(uuid)));
                LOG.info("Synced up to " + progress);
            } catch (IOException e) {
                LOG.info("I/O exception, sleeping for " + sleep + " seconds.");
                SECONDS.sleep(sleep);
                sleep = Math.min(120, sleep * 2);
            }
        }
    }

    private static class UUIDHandler implements ResponseHandler<UUID> {

        public UUID handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
            switch (response.getStatusLine().getStatusCode()) {
            case 200:
                final String body = IOUtils.toString(response.getEntity().getContent());
                final JSONObject json = JSONObject.fromObject(body);
                return UUID.fromString(json.getString("uuid"));
            default:
                return null;
            }
        }

    }

    private static class ProgressHandler implements ResponseHandler<Long> {

        private final IndexWriter writer;

        public ProgressHandler(final IndexWriter writer) {
            this.writer = writer;
        }

        public Long handleResponse(final HttpResponse response) throws IOException {
            final HttpEntity entity = response.getEntity();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"));
            String line;

            Map commit = writer.getReader().getCommitUserData();
            long update_seq = 0, new_seq = 0;
            if (commit.containsKey("update_seq")) {
                update_seq = Long.parseLong((String) commit.get("update_seq"));
                new_seq = update_seq;
            }
            LOG.info("current update_seq is " + update_seq);
            LOG.info("numDocs = " + writer.numDocs());

            while ((line = reader.readLine()) != null) {
                // Commit on heartbeat (or end of sequence).
                if (line.length() == 0) {
                    if (update_seq != new_seq) {
                        commit = new HashMap();
                        commit.put("update_seq", Long.toString(new_seq));
                        writer.commit(commit);
                        LOG.info("committed " + commit);
                        update_seq = new_seq;
                    }
                    continue;
                }

                // Convert the line to JSON.
                final JSONObject json = JSONObject.fromObject(line);

                // Error?
                if (json.has("error")) {
                    LOG.info("error");
                    break;
                }

                // End of feed.
                if (json.has("last_seq")) {
                    break;
                }

                // Update.
                final long seq = json.getLong("seq");
                if (seq > new_seq) {
                    LOG.info("seq: " + seq);
                    final String id = json.getString("id");
                    final Document doc = new Document();
                    doc.add(new NumericField("seq", Store.NO, true).setLongValue(seq));
                    doc.add(new Field("id", id, Store.YES, Index.NOT_ANALYZED_NO_NORMS));
                    LOG.info(doc);
                    writer.updateDocument(new Term("id", id), doc);
                    new_seq = seq;
                } else {
                    LOG.info("Ignoring applied update at seq " + seq);
                }
            }
            LOG.info("handler exiting.");

            return update_seq;
        }
    }

}
