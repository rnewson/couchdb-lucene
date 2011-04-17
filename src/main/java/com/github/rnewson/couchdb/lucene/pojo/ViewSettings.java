package com.github.rnewson.couchdb.lucene.pojo;

import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;

import com.github.rnewson.couchdb.lucene.couchdb.FieldType;

public final class ViewSettings {

	private float boost;
	private String field;
	private Index index;
	private Store store;
	private TermVector termvector;
	private FieldType type;

	public float getBoost() {
		return boost;
	}

	public String getField() {
		return field;
	}

	public Index getIndex() {
		return index;
	}

	public Store getStore() {
		return store;
	}

	public TermVector getTermvector() {
		return termvector;
	}

	public FieldType getType() {
		return type;
	}

	public void setBoost(final float boost) {
		this.boost = boost;
	}

	public void setField(final String field) {
		this.field = field;
	}

	public void setIndex(final Index index) {
		this.index = index;
	}

	public void setStore(final Store store) {
		this.store = store;
	}

	public void setTermvector(final TermVector termvector) {
		this.termvector = termvector;
	}

	public void setType(final FieldType type) {
		this.type = type;
	}

}
