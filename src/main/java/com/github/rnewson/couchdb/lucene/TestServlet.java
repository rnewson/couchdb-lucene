package com.github.rnewson.couchdb.lucene;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.HttpClient;
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

    private Lucene lucene;
    private Database db;

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
                writer.append("All tests passed.");
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
        final HttpClient client = new DefaultHttpClient();
        final Couch couch = new Couch(client, "http://localhost:5984");
        db = couch.getDatabase("lucenetestdb");
        db.delete();
        assertThat("couldn't create database", db.create(), is(true));
    }

    @After
    public void teardown() throws Exception {
        assertThat("couldn't delete database", db.delete(), is(true));
    }

    @Test
    public void test1() {
        assertThat(true, is(true));
    }

}
