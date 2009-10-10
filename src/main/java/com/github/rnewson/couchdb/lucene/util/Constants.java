package com.github.rnewson.couchdb.lucene.util;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

public final class Constants {

    public static final Analyzer ANALYZER = new StandardAnalyzer(Version.LUCENE_CURRENT);

    public static final String CONTENT_TYPE = "application/json";

    public static final String DEFAULT_FIELD = "default";

}
