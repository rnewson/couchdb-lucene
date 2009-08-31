package com.github.rnewson.couchdb.lucene;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.junit.Test;

public class LuceneTest {

    @Test
    public void testNumerics() throws Exception {
        final Directory dir = new RAMDirectory();
        final IndexWriter writer = new IndexWriter(dir, new StandardAnalyzer(Version.LUCENE_CURRENT), true,
                MaxFieldLength.UNLIMITED);
        add(writer, 1);
        add(writer, 2);
        add(writer, 10);
        add(writer, 100);
        writer.close();

        final IndexReader reader = IndexReader.open(dir, true);
        final IndexSearcher searcher = new IndexSearcher(reader);

        final TopDocs td = searcher.search(NumericRangeQuery.newIntRange("int", 2, 10, true, true), 10);
        assertThat(td.totalHits, is(2));

        final TopFieldDocs tfd = searcher.search(NumericRangeQuery.newIntRange("int", 0, 5, true, true), null, 10,
                new Sort(new SortField("int", SortField.INT)));
        assertThat(tfd.totalHits, is(2));

        reader.close();
    }

    private void add(final IndexWriter writer, final int value) throws IOException {
        final Document doc = new Document();
        doc.add(new NumericField("int").setIntValue(value));
        writer.addDocument(doc);
    }

}
