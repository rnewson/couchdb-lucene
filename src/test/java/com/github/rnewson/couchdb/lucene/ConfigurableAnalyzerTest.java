package com.github.rnewson.couchdb.lucene;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

import java.io.Reader;
import java.io.StringReader;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.de.GermanStemFilter;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.junit.Test;

public class ConfigurableAnalyzerTest {

    @Test
    public void testConfigurableAnalyzer() throws Exception {
        final ConfigurableAnalyzer analyzer = new ConfigurableAnalyzer(':', new StandardAnalyzer());
        analyzer.addAnalyzer("fr", new FrenchAnalyzer());
        analyzer.addAnalyzer("de", new GermanAnalyzer());

        final Reader reader = new StringReader("hello");

        assertThat(analyzer.tokenStream("de:hello", reader), instanceOf(GermanStemFilter.class));
        assertThat(analyzer.tokenStream("hello", reader), instanceOf(StopFilter.class));
        assertThat(analyzer.tokenStream("fr:hello", reader), instanceOf(LowerCaseFilter.class));
    }

}
