package com.github.rnewson.couchdb.lucene;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;

public final class ConfigurableAnalyzer extends Analyzer {

    private final Analyzer defaultAnalyzer;
    private final Map<String, Analyzer> map = new HashMap<String, Analyzer>();
    private final char prefixTerminator;

    public ConfigurableAnalyzer(final char prefixTerminator, final Analyzer defaultAnalyzer) {
        this.prefixTerminator = prefixTerminator;
        this.defaultAnalyzer = defaultAnalyzer;
    }

    public void addAnalyzer(final String prefix, final Analyzer analyzer) {
        map.put(prefix, analyzer);
    }

    @Override
    public int getPositionIncrementGap(final String fieldName) {
        return analyzer(fieldName).getPositionIncrementGap(removePrefix(fieldName));
    }

    @Override
    public TokenStream reusableTokenStream(final String fieldName, final Reader reader) throws IOException {
        return analyzer(fieldName).reusableTokenStream(removePrefix(fieldName), reader);
    }

    @Override
    public TokenStream tokenStream(final String fieldName, final Reader reader) {
        return analyzer(fieldName).tokenStream(removePrefix(fieldName), reader);
    }

    private Analyzer analyzer(final String fieldName) {
        final int idx = fieldName.indexOf(prefixTerminator);
        if (idx == -1) {
            return defaultAnalyzer;
        }

        final Analyzer result = map.get(fieldName.substring(0, idx));
        return result != null ? result : defaultAnalyzer;
    }

    private String removePrefix(final String fieldName) {
        final int idx = fieldName.indexOf(prefixTerminator);
        if (idx == -1)
            return fieldName;
        return fieldName.substring(idx);
    }

}
