package com.github.rnewson.couchdb.lucene.v2;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.rnewson.couchdb.lucene.LuceneGateway;
import com.github.rnewson.couchdb.lucene.ViewSignature;
import com.github.rnewson.couchdb.lucene.LuceneGateway.SearcherCallback;
import com.github.rnewson.couchdb.lucene.LuceneGateway.WriterCallback;

public class LuceneGatewayTest {

    private File dir;
    private ViewSignature sig;
    private LuceneGateway gateway;
    private Document doc;

    @Before
    public void setup() {
        sig = ViewSignature.getSignatureByFunction("db1", "function(doc){}");
        doc = new Document();
        doc.add(new Field("id", "12", Store.YES, Index.ANALYZED));

        dir = new File("target", "tmp");
        dir.mkdir();
    }

    @After
    public void cleanup() throws IOException {
        gateway.close();
        FileUtils.cleanDirectory(dir);
    }

    @Test
    public void normalSearch() throws IOException {
        search(false, 0);
    }

    @Test
    public void nearRealtimeSearch() throws IOException {
        search(true, 1);
    }

    private void search(final boolean realtime, final int expectedCount) throws IOException {
        gateway = new LuceneGateway(dir, realtime);
        gateway.withWriter(sig, new WriterCallback<Void>() {

            @Override
            public Void callback(final IndexWriter writer) throws IOException {
                writer.addDocument(doc);
                return null;
            }
        });

        final int count = gateway.withSearcher(sig, !realtime, new SearcherCallback<Integer>() {
            @Override
            public Integer callback(final IndexSearcher searcher) throws IOException {
                return searcher.search(new TermQuery(new Term("id", "12")), 1).totalHits;
            }
        });
        assertThat(count, is(expectedCount));
    }

}
