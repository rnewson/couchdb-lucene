/*
 * Copyright Robert Newson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.rnewson.couchdb.lucene;

import com.github.rnewson.couchdb.lucene.couchdb.FieldType;
import com.github.rnewson.couchdb.lucene.util.Constants;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.PointRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class CustomQueryParserTest {

    private CustomQueryParser parser;

    @Before
    public void setup() {
        parser = new CustomQueryParser("default", new StandardAnalyzer());
    }

    @Test
    public void integerRangeQuery() throws Exception {
        final Query q = parser.parse("blah<int>:[0 TO 123]");
        assertRange(q, Integer.class, 0, 123);
    }

    @Test
    public void longRangeQuery() throws Exception {
        final Query q = parser.parse("blah<long>:[0 TO 123]");
        assertRange(q, Long.class, 0L, 123L);
    }

    @Test
    public void floatRangeQuery() throws Exception {
        final Query q = parser.parse("blah<float>:[0.0 TO 123.5]");
        assertRange(q, Float.class, 0.0f, 123.5f);
    }

    @Test
    public void doubleRangeQuery() throws Exception {
        final Query q = parser.parse("blah<double>:[0.0 TO 123.0]");
        assertRange(q, Double.class, 0.0, 123.0);
    }

    @Test
    public void dateRangeQuery() throws Exception {
        final Query q = parser.parse("blah<date>:[2000-01-01 TO 2010-02-04]");
        assertRange(q, Long.class, time("2000-01-01"), time("2010-02-04"));
    }

    @Test
    public void dateTimeRangeQuery() throws Exception {
        final Query q = parser.parse("blah<date>:[2000-01-01T00:00:01 TO 2010-02-04T00:00:01]");
        assertRange(q, Long.class, time("2000-01-01T00:00:01"), time("2010-02-04T00:00:01"));
    }

    @Test
    public void dateTimeZoneRangeQuery() throws Exception {
        final Query q = parser.parse("blah<date>:[2000-01-01-0100 TO 2010-02-04-0100]");
        assertRange(q, Long.class, time("2000-01-01-0100"), time("2010-02-04-0100"));
    }

    @Test
    public void dateTimeTimeZoneRangeQuery() throws Exception {
        final Query q = parser.parse("blah<date>:[2000-01-01T00:00:00-0100 TO 2010-02-04T00:00:00-0100]");
        assertRange(q, Long.class, time("2000-01-01T00:00:00-0100"), time("2010-02-04T00:00:00-0100"));
    }

    @Test
    public void fieldNameWithDashes() throws Exception {
        final Query q = parser.parse("foo-bar:baz");
        assertThat(q, is(TermQuery.class));
    }

    @Test
    public void fieldNameWithEscapedSpaces() throws Exception {
        final Query q = parser.parse("foo\\ bar:baz");
        assertThat(q, is(TermQuery.class));
    }

    @Test
    public void fieldNameWithNonAscii() throws Exception {
        final Query q = parser.parse("foó:bar");
        assertThat(q, is(TermQuery.class));
    }

    private long time(final String str) throws ParseException {
        return FieldType.toDate(str);
    }

    private void assertRange(final Query q, final Class<?> type, final Number min, final Number max) {
        assertThat(q, is(PointRangeQuery.class));
    }

}
