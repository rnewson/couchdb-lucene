package org.apache.couchdb.lucene;

import static org.junit.Assert.assertThat;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

public class RhinoTest {

	@Test
	public void test() throws Exception {
		final QueryParser qp = new QueryParser("body", new StandardAnalyzer());
		final Query q = qp.parse("field_name:\"-3.2\"");
		System.out.println(((TermQuery) q).getTerm().text());
	}

	@Test
	public void testRhino() {
		final Rhino rhino = new Rhino("function(doc) { delete doc.deleteme; return doc.size; }");
		final String doc = "{\"deleteme\":\"true\", \"size\":13}";
		assertThat(rhino.parse(doc), CoreMatchers.is("13.0"));
		rhino.close();
	}

	@Test
	public void testRhinoActual() {
		final String fn = "function(doc) { " + "out.emit_text(\"body\", doc.body); " + "out.emit_int(\"size\", doc.size); "
				+ "out.emit_date(\"start\", doc.start_date);" + " }";
		System.out.println(fn);
		final Rhino rhino = new Rhino(fn);
		final String doc = "{\"body\":\"some text.\", \"size\":13, \"start_date\":\"2009-05-16 09:14:39 -0000\" }";
		assertThat(rhino.parse(doc), CoreMatchers.is("13.0"));
		rhino.close();
	}

}
