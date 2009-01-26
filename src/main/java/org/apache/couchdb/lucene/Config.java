package org.apache.couchdb.lucene;

import static java.util.concurrent.TimeUnit.MINUTES;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.QueryParser;

final class Config {

	static final long REFRESH_INTERVAL = MINUTES.toMillis(Integer.getInteger("couchdb.lucene.refresh.mins", 5));

	static final Analyzer ANALYZER = new StandardAnalyzer();

	static final QueryParser QP = new QueryParser("text", ANALYZER);

	static final String DB = "_db";

	static final String ID = "_id";

	static final String REV = "_rev";

	static final String SEQ = "_seq";

	static final int RAM_BUF = Integer.getInteger("couchdb.lucene.ram", 200);

	static final int BATCH_SIZE = Integer.getInteger("couchdb.lucene.batch", 100);

	static final String DB_URL = System.getProperty("couchdb.url", "http://localhost:5984");
}
