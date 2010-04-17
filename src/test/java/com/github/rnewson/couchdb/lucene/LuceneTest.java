package com.github.rnewson.couchdb.lucene;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Collections;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.junit.Test;

public class LuceneTest {

	@Test
	public void forceCommit() throws Exception {
		final Directory dir = new RAMDirectory();
		final IndexWriter writer = new IndexWriter(dir, new StandardAnalyzer(
				Version.LUCENE_30), MaxFieldLength.UNLIMITED);

		writer.commit(Collections.singletonMap("foo", "bar"));
		assertThat(IndexReader.getCommitUserData(dir).get("foo"),
				is(nullValue()));
		
		final Document doc = new Document();
		doc.add(new Field("foo", "bar", Store.NO, Index.NOT_ANALYZED_NO_NORMS));
		final Term term = new Term("foo", "bar");
		writer.updateDocument(term, doc);
		writer.commit(Collections.singletonMap("foo", "bar"));
		assertThat(IndexReader.getCommitUserData(dir).get("foo"), is("bar"));
		assertThat(writer.numDocs(), is(1));
		writer.deleteDocuments(term);
		writer.commit();
		assertThat(writer.numDocs(), is(0));
	}

}
