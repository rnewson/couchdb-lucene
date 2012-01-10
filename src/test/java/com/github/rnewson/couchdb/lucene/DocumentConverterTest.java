package com.github.rnewson.couchdb.lucene;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Collection;
import java.util.TimeZone;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericField;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;

import com.github.rnewson.couchdb.lucene.couchdb.CouchDocument;
import com.github.rnewson.couchdb.lucene.couchdb.View;
import com.github.rnewson.couchdb.lucene.couchdb.ViewSettings;
import com.github.rnewson.couchdb.lucene.util.Constants;

public class DocumentConverterTest {

    private Context context;

    private TimeZone tz;

    @Before
    public void setup() {
        context = Context.enter();
        tz = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
    }

    @After
    public void teardown() {
        TimeZone.setDefault(tz);
        Context.exit();
    }

    @Test
    public void testSingleDocumentReturn() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {return new Document();}"));
        final Collection<Document> result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.size(), is(1));
        assertThat(result.iterator().next().get("_id"), is("hello"));
    }

    @Test
    public void testMultipleDocumentReturn() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret = new Array(); ret.push(new Document()); ret.push(new Document()); return ret;}"));
        final Collection<Document> result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.size(), is(2));
        assertThat(result.iterator().next().get("_id"), is("hello"));
    }

    @Test
    public void testAdd() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret=new Document(); ret.add(doc.key); return ret;}"));
        final Collection<Document> result = converter.convert(
                doc("{_id:\"hello\", key:\"value\"}"),
                settings(),
                null);
        assertThat(result.size(), is(1));
        assertThat(result.iterator().next().get(Constants.DEFAULT_FIELD), is("value"));
    }

    @Test
    public void testForLoopOverObject() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret=new Document(); for (var key in doc) { ret.add(doc[key]); } return ret; }"));
        final Collection<Document> result = converter.convert(
                doc("{_id:\"hello\", key:\"value\"}"),
                settings(),
                null);
        assertThat(result.size(), is(1));
        assertThat(result.iterator().next().get("_id"), is("hello"));
        assertThat(result.iterator().next().getValues(Constants.DEFAULT_FIELD)[0], is("hello"));
        assertThat(result.iterator().next().getValues(Constants.DEFAULT_FIELD)[1], is("value"));
    }

    @Test
    public void testForLoopOverArray() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret=new Document(); for (var key in doc.arr) {ret.add(doc.arr[key]); } return ret; }"));
        final Collection<Document> result = converter.convert(
                doc("{_id:\"hello\", arr:[0,1,2,3]}"),
                settings(),
                null);
        assertThat(result.size(), is(1));
        assertThat(result.iterator().next().get("_id"), is("hello"));
        assertThat(result.iterator().next().getValues(Constants.DEFAULT_FIELD)[0], is("0"));
        assertThat(result.iterator().next().getValues(Constants.DEFAULT_FIELD)[1], is("1"));
        assertThat(result.iterator().next().getValues(Constants.DEFAULT_FIELD)[2], is("2"));
        assertThat(result.iterator().next().getValues(Constants.DEFAULT_FIELD)[3], is("3"));
    }

    @Test
    public void testForEverything() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret=new Document(); "
                     + "function idx(obj) {for (var key in obj) "
                     + "{switch (typeof obj[key]) {case 'object':idx(obj[key]); break; "
                     + "case 'function': break; default: ret.add(obj[key]); break;} } }; idx(doc); return ret; }"));

        final Collection<Document> result = converter.convert(
                doc("{_id:\"hello\", l1: { l2: {l3:[\"v3\", \"v4\"]}}}"),
                settings(),
                null);
        assertThat(result.iterator().next().getValues(Constants.DEFAULT_FIELD)[0], is("hello"));
        assertThat(result.iterator().next().getValues(Constants.DEFAULT_FIELD)[1], is("v3"));
        assertThat(result.iterator().next().getValues(Constants.DEFAULT_FIELD)[2], is("v4"));
    }

    @Test
    public void testNullReturn() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {return null;}"));
        final Collection<Document> result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.size(), is(0));
    }

    @Test
    public void testUndefinedReturn() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {return doc.nope;}"));
        final Collection<Document> result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.size(), is(0));
    }

    @Test
    public void testRuntimeException() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {throw {bad : \"stuff\"}}"));
        final Collection<Document> result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.size(), is(0));
    }

    @Test
    public void testJSONStringify() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret=new Document(); "
                        + " ret.add(JSON.stringify({\"foo\":\"bar\"}), {\"field\":\"s\",\"store\":\"yes\"}); return ret;}"));
        final Collection<Document> result = converter.convert(
                doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.size(), is(1));
        assertThat(result.iterator().next().getValues("s")[0], is("{\"foo\":\"bar\"}"));
    }

    @Test(expected=EvaluatorException.class)
    public void testBadCode() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) { if (doc.) return null; }"));
        converter.convert(doc("{_id:\"hello\"}"), settings(), null);
    }

    @Test
    public void testNullAddsAreIgnored() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("function(doc) {var ret=new Document(); ret.add(doc.nope); return ret;}"));
        final Collection<Document> result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.size(), is(1));
    }

    @Test
    public void testQuoteRemoval() throws Exception {
        final DocumentConverter converter = new DocumentConverter(
                context,
                view("\"function(doc) {return new Document();}\""));
        final Collection<Document> result = converter.convert(doc("{_id:\"hello\"}"), settings(), null);
        assertThat(result.size(), is(1));
        assertThat(result.iterator().next().get("_id"), is("hello"));
    }

    @Test
    public void testNoReturnValue() throws Exception {
        final String fun = "function(doc) { }";
        final DocumentConverter converter = new DocumentConverter(context, view(fun));
        final Collection<Document> result = converter.convert(doc("{_id:\"hi\"}"), settings(), null);
        assertThat(result.size(), is(0));
    }

    @Test
    public void testDefaultValue() throws Exception {
        final String fun = "function(doc) { var ret=new Document(); ret.add(doc['arr'].join(' '));  return ret; }";
        final DocumentConverter converter = new DocumentConverter(context, view(fun));
        final Collection<Document> result = converter.convert(
                doc("{_id:\"hi\", arr:[\"1\",\"2\"]}"),
                settings(),
                null);
        assertThat(result.size(), is(1));
        assertThat(result.iterator().next().get("default"), is("1 2"));
    }
    
    @Test
    public void testNullValue() throws Exception {
    	 final String fun = "function(doc) { var ret=new Document(); ret.add(doc.foo);  return ret; }";
         final DocumentConverter converter = new DocumentConverter(context, view(fun));
         final Collection<Document> result = converter.convert(
                 doc("{_id:\"hi\", foo:null}"),
                 settings(),
                 null);
         assertThat(result.size(), is(1));
         assertThat(result.iterator().next().get("foo"), is(nullValue()));
    }
    
    @Test
    public void testLongValue() throws Exception {
    	 final String fun = "function(doc) { var ret=new Document(); ret.add(12, {type:\"long\", field:\"num\"});  return ret; }";
         final DocumentConverter converter = new DocumentConverter(context, view(fun));
         final Collection<Document> result = converter.convert(
                 doc("{_id:\"hi\"}"),
                 settings(),
                 null);
         assertThat(result.size(), is(1));
         assertThat(result.iterator().next().getFieldable("num"), is(NumericField.class));
    }
    
    @Test
    public void testDateString() throws Exception {
    	 final String fun = "function(doc) { var ret=new Document(); ret.add(\"2009-01-01\", {type:\"date\", field:\"num\"});  return ret; }";
         final DocumentConverter converter = new DocumentConverter(context, view(fun));
         final Collection<Document> result = converter.convert(
                 doc("{_id:\"hi\"}"),
                 settings(),
                 null);
         assertThat(result.size(), is(1));
         assertThat(result.iterator().next().getFieldable("num"), is(NumericField.class));
    }
    
    @Test
    public void testDateObject() throws Exception {
    	 final String fun = "function(doc) { var ret=new Document(); ret.add(new Date(2010,8,13), {type:\"date\", field:\"num\"});  return ret; }";
         final DocumentConverter converter = new DocumentConverter(context, view(fun));
         final Collection<Document> result = converter.convert(
                 doc("{_id:\"hi\"}"),
                 settings(),
                 null);
         assertThat(result.size(), is(1));
         assertThat(result.iterator().next().getFieldable("num"), is(NumericField.class));
         assertThat((Long)((NumericField)result.iterator().next().getFieldable("num")).getNumericValue(), is(1284332400000L));
    }
    
    @Test
    public void testDateObject2() throws Exception {
         final String fun = "function(doc) { var ret=new Document(); ret.add(new Date(\"January 6, 1972 16:05:00\"), {type:\"date\", field:\"num\"});  return ret; }";
         final DocumentConverter converter = new DocumentConverter(context, view(fun));
         final Collection<Document> result = converter.convert(
                 doc("{_id:\"hi\"}"),
                 settings(),
                 null);
         assertThat(result.size(), is(1));
         assertThat(result.iterator().next().getFieldable("num"), is(NumericField.class));
         assertThat((Long)((NumericField)result.iterator().next().getFieldable("num")).getNumericValue(), is(63561900000L));
    }

    @Test
    public void testParseInt() throws Exception {
    	 final String fun = "function(doc) { var ret=new Document(); ret.add(parseInt(\"12.5\"), {type:\"int\", field:\"num\"});  return ret; }";
         final DocumentConverter converter = new DocumentConverter(context, view(fun));
         final Collection<Document> result = converter.convert(
                 doc("{_id:\"hi\"}"),
                 settings(),
                 null);
         assertThat(result.size(), is(1));
         assertThat(result.iterator().next().getFieldable("num"), is(NumericField.class));
    }
    
	@Test
	public void testConditionalOnNulls() throws Exception {
		final String fun = "function(doc) { if (doc.foo && doc.bar) { return new Document(); }; return null; }";
		final DocumentConverter converter = new DocumentConverter(context,
				view(fun));
		final Collection<Document> result = converter.convert(
				doc("{_id:\"hi\", foo: null, bar: null}"), settings(), null);
		assertThat(result.size(), is(0));
	}

    private CouchDocument doc(final String json) throws JSONException {
        return new CouchDocument(new JSONObject(json));
    }

    private ViewSettings settings() {
        return ViewSettings.getDefaultSettings();
    }

    private View view(final String fun) throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("index", fun);
        return new View(null, json);
    }

}
