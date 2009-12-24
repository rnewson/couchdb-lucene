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
        assertRange(q, Integer.class, 0, 123);
    }

    @Test
    public void longRangeQuery() throws Exception {
        final Query q = parser.parse("blah:[0L TO 123L]");
        assertRange(q, Long.class, 0L, 123L);
    }

    @Test
    public void floatRangeQuery() throws Exception {
        final Query q = parser.parse("blah:[0.0f TO 123.5f]");
        assertRange(q, Float.class, 0.0f, 123.5f);
    }

    @Test
    public void doubleRangeQuery() throws Exception {
        final Query q = parser.parse("blah:[0.0 TO 123.0]");
        assertRange(q, Double.class, 0.0, 123.0);
    }

    @Test
    public void dateRangeQuery() throws Exception {
        final Query q = parser.parse("blah:[2000-01-01 TO 2010-02-04]");
        assertRange(q, Long.class, 946684800000L, 1265241600000L);
    }

    @Test
    public void dateTimeRangeQuery() throws Exception {
        final Query q = parser.parse("blah:[2000-01-01T00:00:01 TO 2010-02-04T00:00:01]");
        assertRange(q, Long.class, 946684801000L, 1265241601000L);
    }

    @Test
    public void dateTimeZoneRangeQuery() throws Exception {
        final Query q = parser.parse("blah:[2000-01-01-0100 TO 2010-02-04-0100]");
        assertRange(q, Long.class, 946688400000L, 1265245200000L);
    }

    @Test
    public void dateTimeTimeZoneRangeQuery() throws Exception {
        final Query q = parser.parse("blah:[2000-01-01T00:00:00-0100 TO 2010-02-04T00:00:00-0100]");
        assertRange(q, Long.class, 946688400000L, 1265245200000L);
    }

    private void assertRange(final Query q, final Class<?> type, final Number min, final Number max) {
        assertThat(q, is(NumericRangeQuery.class));
        final NumericRangeQuery nq = (NumericRangeQuery) q;
        assertThat(nq.getMin(), is(type));
        assertThat(nq.getMax(), is(type));
        assertThat(nq.getMin(), is(min));
        assertThat(nq.getMax(), is(max));
    }

}
