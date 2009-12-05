package com.github.rnewson.couchdb.lucene;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import com.github.rnewson.couchdb.lucene.couchdb.Couch;
import com.github.rnewson.couchdb.lucene.couchdb.Database;

public final class TestServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // TODO get this from getPathInfo()
    private static final String URL = "http://localhost:5984/";

    private Lucene lucene;
    private Database db;

    private DefaultHttpClient client;

    public void setLucene(final Lucene lucene) {
        this.lucene = lucene;
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final Result result = JUnitCore.runClasses(TestServlet.class);
        resp.setStatus(200);
        resp.setContentType("text/plain");
        final Writer writer = resp.getWriter();
        try {
            if (result.wasSuccessful()) {
                writer.append("All tests passed.\n");
            } else {
                for (final Failure failure : result.getFailures()) {
                    writer.append(failure.toString());
                    writer.append("\n");
                }
            }
        } finally {
            writer.close();
        }
    }

    @Before
    public void setup() throws Exception {
        client = new DefaultHttpClient();
        final Couch couch = Couch.getInstance(client, URL);
        db = couch.getDatabase("lucenetestdb");
        db.delete();
        assertThat("couldn't create database", db.create(), is(true));
    }

    @After
    public void teardown() throws Exception {
        // assertThat("couldn't delete database", db.delete(), is(true));
    }

    @Test
    public void basicIndexing() throws Exception {
        assertThat("can't save ddoc.", db.saveDocument("_design/ddoc", fix("{'fulltext':{'by_subject':"
                + "{'index':'function(doc) { var ret = new Document(); ret.add(doc.subject); return ret;}'}}}")), is(true));
        assertThat("can't save doc1.", db.saveDocument("doc1", fix("{'subject':'cat dog'}")), is(true));

        final HttpGet get = new HttpGet("http://localhost:5985/search/localhost/5984/lucenetestdb/ddoc/by_subject?q=cat");
        final String response = client.execute(get, new BasicResponseHandler());
        final JSONObject result = JSONObject.fromObject(response);
        assertThat(result.getLong("total_rows"), is(1L));
    }

    private String fix(final String str) {
        return str.replaceAll("'", "\"");
    }

}
