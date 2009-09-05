package com.github.rnewson.couchdb.lucene.v2.eval;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;

public final class Document {

    private final org.apache.lucene.document.Document delegate;

    public Document() {
        this.delegate = new org.apache.lucene.document.Document();
    }

    public void add(final String string) {
        delegate.add(new Field("default", string, Store.NO, Index.ANALYZED));
    }

    public void add(final String string, final Object settings) {

    }

    @Override
    public String toString() {
        return delegate.toString();
    }

}
