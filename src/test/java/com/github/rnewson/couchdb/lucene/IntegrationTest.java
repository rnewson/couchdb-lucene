package com.github.rnewson.couchdb.lucene;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;

import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * This test class requires a running couchdb instance on localhost port 5984.
 * 
 * @author rnewson
 * 
 */
public class IntegrationTest {

    private final String base = "http://localhost:5984/";
    private final String dbname = "lucenetestdb";

    private Database db;

    @Before
    public void setup() throws IOException, InterruptedException {
        final File dir = new File("target/output");
        FileUtils.cleanDirectory(dir);
        System.setProperty("couchdb.lucene.dir", dir.getAbsolutePath());
        db = new Database(base);
        try {
            db.deleteDatabase(dbname);
            db.createDatabase(dbname);
        } catch (final IOException e) {
            assumeTrue(false);
        }
    }

    @After
    public void teardown() throws IOException {
        // db.deleteDatabase(dbname);
    }

    @Test
    public void index() throws IOException, InterruptedException {
        final String ddoc = "{\"fulltext\": {\"idx\": {\"index\":\"function(doc) {var ret=new Document(); ret.add(doc.content); return ret;}\"}}}";
        assertThat(db.saveDocument(dbname, "_design/lucene", ddoc), is(true));
        for (int i = 0; i < 50; i++) {
            assertThat(db.saveDocument(dbname, "doc-" + i, "{\"content\":\"hello\"}"), is(true));
        }
        SECONDS.sleep(5);

        final JSONObject indexState = db.getDoc(dbname, "_fti");
        assertThat(indexState.getInt("doc_count"), is(51));
    }

}
