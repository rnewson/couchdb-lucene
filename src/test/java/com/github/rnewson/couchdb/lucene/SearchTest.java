package com.github.rnewson.couchdb.lucene;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;
import org.junit.Test;

public class SearchTest {

	@Test
	public void tff() throws Exception {
		final QueryParser qp = new QueryParser("body", new StandardAnalyzer());
		final Query q = qp.parse("\"hello whups thin*\"");
		System.out.println(q);
	}

}
