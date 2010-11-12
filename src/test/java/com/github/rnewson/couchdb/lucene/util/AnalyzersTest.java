package com.github.rnewson.couchdb.lucene.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
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

}
