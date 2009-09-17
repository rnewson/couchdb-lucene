package com.github.rnewson.couchdb.lucene.v2;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

final class Constants {

    static final Analyzer ANALYZER = new StandardAnalyzer(Version.LUCENE_CURRENT);
    
    static final String CONTENT_TYPE = "application/json";

    static final String DEFAULT_FIELD = "default";

    static final String DB = "_db";

    static final String ID = "_id";

    static final String VIEW = "_view";

    static final String SEQ = "_seq";
    
    static final int SEQ_PRECISION = 10;

}
