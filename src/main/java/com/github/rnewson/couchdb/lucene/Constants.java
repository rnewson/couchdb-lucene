package com.github.rnewson.couchdb.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

final class Constants {

    static final Analyzer ANALYZER = new StandardAnalyzer(Version.LUCENE_CURRENT);

    static final String CONTENT_TYPE = "application/json";

    static final String DEFAULT_FIELD = "default";

}
