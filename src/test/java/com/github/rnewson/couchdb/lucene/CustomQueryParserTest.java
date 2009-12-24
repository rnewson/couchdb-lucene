package com.github.rnewson.couchdb.lucene;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import org.junit.Before;
import org.junit.Test;

public class CustomQueryParserTest {

    private CustomQueryParser parser;

    @Before
    public void setup() {
        parser = new CustomQueryParser(Version.LUCENE_CURRENT, "default", new StandardAnalyzer(Version.LUCENE_CURRENT));
    }

    @Test
    public void integerRangeQuery() throws Exception {
        final Query q = parser.parse("blah:[0 TO 123]");
        assertThat(q, is(NumericRangeQuery.class));
        assertThat(((NumericRangeQuery) q).getMin(), is(Integer.class));
    }

    @Test
    public void longRangeQuery() throws Exception {
        final Query q = parser.parse("blah:[0L TO 123L]");
        assertThat(q, is(NumericRangeQuery.class));
        assertThat(((NumericRangeQuery) q).getMin(), is(Long.class));
    }

    @Test
    public void floatRangeQuery() throws Exception {
        final Query q = parser.parse("blah:[0.0f TO 123.5f]");
        assertThat(q, is(NumericRangeQuery.class));
        assertThat(((NumericRangeQuery) q).getMin(), is(Float.class));
    }

    @Test
    public void doubleRangeQuery() throws Exception {
        final Query q = parser.parse("blah:[0.0 TO 123.0]");
        assertThat(q, is(NumericRangeQuery.class));
        assertThat(((NumericRangeQuery) q).getMin(), is(Double.class));
    }

}
