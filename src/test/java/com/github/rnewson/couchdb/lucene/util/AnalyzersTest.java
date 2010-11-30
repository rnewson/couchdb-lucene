package com.github.rnewson.couchdb.lucene.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

import java.io.StringReader;
import java.util.Iterator;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.snowball.SnowballAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.junit.Test;

public class AnalyzersTest {

    @Test
    public void testStandard() throws Exception {
        assertThat(Analyzers.getAnalyzer("standard"), is(StandardAnalyzer.class));
    }

    @Test
    public void testFrench() throws Exception {
        assertThat(Analyzers.getAnalyzer("french"), is(FrenchAnalyzer.class));
    }

    @Test
    public void testWhitespace() throws Exception {
        assertThat(Analyzers.getAnalyzer("whitespace"), is(WhitespaceAnalyzer.class));
    }

    @Test
    public void testPerField() throws Exception {
        final Analyzer analyzer = Analyzers.getAnalyzer("perfield:{name:\"standard\",age:\"keyword\"}");
        assertThat(analyzer, is(PerFieldAnalyzerWrapper.class));
        assertThat(analyzer.toString(), containsString("default=org.apache.lucene.analysis.standard.StandardAnalyzer"));
        assertThat(analyzer.toString(), containsString("name=org.apache.lucene.analysis.standard.StandardAnalyzer"));
        assertThat(analyzer.toString(), containsString("age=org.apache.lucene.analysis.KeywordAnalyzer"));
    }

    @Test
    public void testPerFieldDefault() throws Exception {
        final Analyzer analyzer = Analyzers.getAnalyzer("perfield:{default:\"keyword\"}");
        assertThat(analyzer, is(PerFieldAnalyzerWrapper.class));
        assertThat(analyzer.toString(), containsString("default=org.apache.lucene.analysis.KeywordAnalyzer"));
    }
    
    @Test
    public void testPorter() throws Exception {
        assertThat(Analyzers.getAnalyzer("porter"), is(PorterStemAnalyzer.class));
        assertAnalysisOf(Analyzers.getAnalyzer("porter"), "foo bar", "foo", "bar");
        assertAnalysisOf(Analyzers.getAnalyzer("porter"), "walking", "walk");
    }
    
    @Test
    public void testSnowball() throws Exception {
        assertThat(Analyzers.getAnalyzer("snowball"), is(SnowballAnalyzer.class));
        assertAnalysisOf(Analyzers.getAnalyzer("snowball"), "foo bar", "foo", "bar");
    }

    private void assertAnalysisOf(final Analyzer analyzer, final String input, final String... expected) throws Exception {
        final TokenStream stream = analyzer.tokenStream("foo", new StringReader(input));
        int i=0;
        while (stream.incrementToken()) {
            final TermAttribute termAttribute = stream.getAttribute(TermAttribute.class);
            assertThat(termAttribute.term(), is(expected[i++]));
        }
        stream.close();
    }

}
