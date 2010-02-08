package com.github.rnewson.couchdb.lucene;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import net.sf.json.JSONObject;

import org.apache.lucene.document.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;

import com.github.rnewson.couchdb.lucene.couchdb.CouchDocument;
import com.github.rnewson.couchdb.lucene.couchdb.View;
import com.github.rnewson.couchdb.lucene.couchdb.ViewSettings;
import com.github.rnewson.couchdb.lucene.util.Constants;

public class DocumentConverterTest {

    private Context context;

    @Before
    public void setup() {
        context = Context.enter();
    }

    @After
    public void teardown() {
        Context.exit();
    }

    @Test
    public void testSingleDocumentReturn() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {return new Document();}"));
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.length, is(1));
        assertThat(result[0].get("_id"), is("hello"));
    }

    @Test
    public void testMultipleDocumentReturn() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret = new Array(); ret.push(new Document()); ret.push(new Document()); return ret;}"));
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.length, is(2));
        assertThat(result[0].get("_id"), is("hello"));
    }

    @Test
    public void testAdd() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret=new Document(); ret.add(doc.key); return ret;}"));
        final Document[] result = converter.convert(
                doc("{_id:\"hello\", key:\"value\"}"),
                settings(),
                null);
        assertThat(result.length, is(1));
        assertThat(result[0].get(Constants.DEFAULT_FIELD), is("value"));
    }

    @Test
    public void testForLoopOverObject() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret=new Document(); for (var key in doc) { ret.add(doc[key]); } return ret; }"));
        final Document[] result = converter.convert(
                doc("{_id:\"hello\", key:\"value\"}"),
                settings(),
                null);
        assertThat(result.length, is(1));
        assertThat(result[0].get("_id"), is("hello"));
        assertThat(result[0].getValues(Constants.DEFAULT_FIELD)[0], is("hello"));
        assertThat(result[0].getValues(Constants.DEFAULT_FIELD)[1], is("value"));
    }

    @Test
    public void testForLoopOverArray() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret=new Document(); for (var key in doc.arr) {ret.add(doc.arr[key]); } return ret; }"));
        final Document[] result = converter.convert(
                doc("{_id:\"hello\", arr:[0,1,2,3]}"),
                settings(),
                null);
        assertThat(result.length, is(1));
        assertThat(result[0].get("_id"), is("hello"));
        assertThat(result[0].getValues(Constants.DEFAULT_FIELD)[0], is("0"));
        assertThat(result[0].getValues(Constants.DEFAULT_FIELD)[1], is("1"));
        assertThat(result[0].getValues(Constants.DEFAULT_FIELD)[2], is("2"));
        assertThat(result[0].getValues(Constants.DEFAULT_FIELD)[3], is("3"));
    }

    @Test
    public void testForEverything() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret=new Document(); "
                     + "function idx(obj) {for (var key in obj) "
                     + "{switch (typeof obj[key]) {case 'object':idx(obj[key]); break; "
                     + "case 'function': break; default: ret.add(obj[key]); break;} } }; idx(doc); return ret; }"));

        final Document[] result = converter.convert(
                doc("{_id:\"hello\", l1: { l2: {l3:[\"v3\", \"v4\"]}}}"),
                settings(),
                null);
        assertThat(result[0].getValues(Constants.DEFAULT_FIELD)[0], is("hello"));
        assertThat(result[0].getValues(Constants.DEFAULT_FIELD)[1], is("v3"));
        assertThat(result[0].getValues(Constants.DEFAULT_FIELD)[2], is("v4"));
    }

    @Test
    public void testNullReturn() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {return null;}"));
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.length, is(0));
    }

    @Test
    public void testUndefinedReturn() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {return doc.nope;}"));
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.length, is(0));
    }

    @Test
    public void testRuntimeException() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {throw {bad : \"stuff\"}}"));
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.length, is(0));
    }

    @Test
    public void testNullAddsAreIgnored() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret=new Document(); ret.add(doc.nope); return ret;}"));
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.length, is(1));
    }

    @Test
    public void testQuoteRemoval() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("\"function(doc) {return new Document();}\""));
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.length, is(1));
        assertThat(result[0].get("_id"), is("hello"));
    }

    @Test
    public void testNoReturnValue() throws Exception {
        final String fun = "function(doc) { }";
        final DocumentConverter converter = new DocumentConverter(context, view(fun));
        final Document[] result = converter.convert(doc("{_id:\"hi\"}"), settings(), null);
        assertThat(result.length, is(0));
    }

    @Test
    public void defaultValue() throws Exception {
        final String fun = "function(doc) { var ret=new Document(); ret.add(doc['arr'].join(' '));  return ret; }";
        final DocumentConverter converter = new DocumentConverter(context, view(fun));
        final Document[] result = converter.convert(
                doc("{_id:\"hi\", arr:[\"1\",\"2\"]}"),
                settings(),
                null);
        assertThat(result.length, is(1));
        assertThat(result[0].get("default"), is("1 2"));
    }

    private CouchDocument doc(final String json) {
        return new CouchDocument(JSONObject.fromObject(json));
    }

    private ViewSettings settings() {
        return ViewSettings.getDefaultSettings();
    }

    private View view(final String fun) {
        final JSONObject json = new JSONObject();
        json.put("index", fun);
        return new View(json);
    }

}
