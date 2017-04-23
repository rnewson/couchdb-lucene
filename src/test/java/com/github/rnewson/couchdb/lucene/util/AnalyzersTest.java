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

package com.github.rnewson.couchdb.lucene.util;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.json.JSONObject;
import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

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
        assertThat(analyzer.toString(), containsString("age=org.apache.lucene.analysis.core.KeywordAnalyzer"));
    }

    @Test
    public void testPerFieldDefault() throws Exception {
        final Analyzer analyzer = Analyzers.getAnalyzer("perfield:{default:\"keyword\"}");
        assertThat(analyzer, is(PerFieldAnalyzerWrapper.class));
        assertThat(analyzer.toString(), containsString("default=org.apache.lucene.analysis.core.KeywordAnalyzer"));
    }

    @Test
    public void testNGramInstance() throws Exception {
        final Analyzer analyzer = Analyzers.getAnalyzer("ngram");
        assertThat(analyzer.toString(), containsString("NGramAnalyzer"));
    }

    @Test
    public void testClassInstance() throws Exception {
    	final JSONObject obj = new JSONObject("{ \"class\": \"org.apache.lucene.analysis.core.KeywordAnalyzer\" }");
    	final Analyzer analyzer = Analyzers.getAnalyzer(obj);
    	assertThat(analyzer, is(KeywordAnalyzer.class));
    }

    @Test
    public void testClassInstance2() throws Exception {
    	final JSONObject obj = new JSONObject("{ \"class\": \"org.apache.lucene.analysis.nl.DutchAnalyzer\", \"params\": [] }");
    	final Analyzer analyzer = Analyzers.getAnalyzer(obj);
    	assertThat(analyzer, is(org.apache.lucene.analysis.nl.DutchAnalyzer.class));
    }

    @Test
    public void testClassInstance3() throws Exception {
    	final JSONObject obj = 
    	  new JSONObject("{ \"class\": \"org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer\", \"params\": [ { \"name\": \"useDefaultStopWords\", \"type\": \"boolean\", \"value\": true } ] }");
    	final Analyzer analyzer = Analyzers.getAnalyzer(obj);
    	assertThat(analyzer, is(org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer.class));
    }

    @Test
    public void testClassInstance4() throws Exception {
    	final JSONObject obj = new JSONObject("{ \"german\": {} }");
    	final Analyzer analyzer = Analyzers.getAnalyzer(obj);
    	assertThat(analyzer, is(org.apache.lucene.analysis.de.GermanAnalyzer.class));
    }

    @Test
    public void testClassInstance5() throws Exception {
    	final JSONObject obj = new JSONObject("{ \"cjk\": \"\" }");
    	final Analyzer analyzer = Analyzers.getAnalyzer(obj);
    	assertThat(analyzer, is(org.apache.lucene.analysis.cjk.CJKAnalyzer.class));
    }

    @Test
    public void testClassInstance6() throws Exception {
    	final JSONObject obj = new JSONObject("{ \"ngram\": { \"analyzer\": \"simple\", \"min\": 2, \"max\": 3 } }");
    	final Analyzer analyzer = Analyzers.getAnalyzer(obj);
    	assertThat(analyzer.toString(), containsString("NGramAnalyzer"));
    }

    @Test
    public void testClassInstance7() throws Exception {
        final Analyzer analyzer = Analyzers.getAnalyzer("perfield:{default:\"keyword\", lang_bo:{\"class\":\"org.apache.lucene.analysis.core.WhitespaceAnalyzer\"}, lang_sa:{\"class\":\"org.apache.lucene.analysis.hi.HindiAnalyzer\"}}");
        assertThat(analyzer, is(PerFieldAnalyzerWrapper.class));
        assertThat(analyzer.toString(), containsString("default=org.apache.lucene.analysis.core.KeywordAnalyzer"));
        assertThat(analyzer.toString(), containsString("lang_bo=org.apache.lucene.analysis.core.WhitespaceAnalyzer"));
        assertThat(analyzer.toString(), containsString("lang_sa=org.apache.lucene.analysis.hi.HindiAnalyzer"));
    }

    @Test
    public void testClassInstance8() throws Exception {
    	final Analyzer analyzer = Analyzers.fromSpec("{\"perfield\":{\"default\": \"keyword\",\"lang_bo\": {\"class\": \"org.apache.lucene.analysis.core.WhitespaceAnalyzer\"},\"lang_sa\": {\"class\": \"org.apache.lucene.analysis.hi.HindiAnalyzer\"}}}");
        assertThat(analyzer, is(PerFieldAnalyzerWrapper.class));
        assertThat(analyzer.toString(), containsString("default=org.apache.lucene.analysis.core.KeywordAnalyzer"));
        assertThat(analyzer.toString(), containsString("lang_bo=org.apache.lucene.analysis.core.WhitespaceAnalyzer"));
        assertThat(analyzer.toString(), containsString("lang_sa=org.apache.lucene.analysis.hi.HindiAnalyzer"));
    }

    @Test
    public void testNGramTokens() throws Exception {
        assertThat(analyze("ngram:{\"analyzer\":\"simple\"}", "hey there"), is(new String[]{"h", "he", "e", "ey", "y", "t", "th", "h", "he", "e", "er", "r", "re", "e"}));
    }

    @Test
    public void testNGramMinMax() throws Exception {
        assertThat(analyze("ngram:{\"analyzer\":\"simple\",\"min\":2,\"max\":3}", "hello there"), is(new String[]{"he", "hel", "el", "ell", "ll", "llo", "lo", "th", "the", "he", "her", "er", "ere", "re"}));
    }

    @Test
    public void testEmailAddresses() throws Exception {
        assertThat(analyze("standard", "foo@bar.com"), is(new String[]{"foo", "bar.com"}));
        assertThat(analyze("classic", "foo@bar.com"), is(new String[]{"foo@bar.com"}));
    }

    private String[] analyze(final String analyzerName, final String text) throws Exception {
        final Analyzer analyzer = Analyzers.getAnalyzer(analyzerName);
        final TokenStream stream = analyzer.tokenStream("default", new StringReader(text));
        stream.reset();
        final List<String> result = new ArrayList<String>();
        while (stream.incrementToken()) {
            final CharTermAttribute c = stream.getAttribute(CharTermAttribute.class);
            result.add(c.toString());
        }
        return result.toArray(new String[0]);
    }
}
