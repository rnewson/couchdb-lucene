package org.apache.couchdb.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.QueryParser;

final class Config {

	static final long REFRESH_INTERVAL = Integer.getInteger("couchdb.lucene.refresh.mins", 5) * 60000;

	static final Analyzer ANALYZER = new StandardAnalyzer();

	static final String DEFAULT_FIELD = System.getProperty("couchdb.lucene.default_field", "body");

	static final QueryParser QP = new QueryParser(DEFAULT_FIELD, ANALYZER);

	static final String DB = "_db";

	static final String ID = "_id";

	static final String REV = "_rev";

	static final String SEQ = "_seq";

	static final String BODY = "_body";

	static final String TITLE = "_title";

	static final String AUTHOR = "_author";

	static final String INDEX_DIR = System.getProperty("couchdb.lucene.dir", "lucene");

	static final int EXPUNGE_LIMIT = Integer.getInteger("couchdb.lucene.expunge", 1000);

	static final int RAM_BUF = Integer.getInteger("couchdb.lucene.ram", 256);

	static final int BATCH_SIZE = Integer.getInteger("couchdb.lucene.batch", 250);

	static final String DB_URL = System.getProperty("couchdb.url", "http://localhost:5984");

	static final int MAX_LIMIT = Integer.getInteger("couchdb.lucene.max_fetch", 250);

}
