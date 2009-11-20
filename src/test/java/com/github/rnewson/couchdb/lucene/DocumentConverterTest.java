package com.github.rnewson.couchdb.lucene;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import net.sf.json.JSONObject;

import org.apache.lucene.document.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;

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
        final DocumentConverter converter = new DocumentConverter(context, "single", "function(doc) {return new Document();}");
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), new JSONObject(), null);
        assertThat(result.length, is(1));
        assertThat(result[0].get("_id"), is("hello"));
    }

    @Test
    public void testMultipleDocumentReturn() throws Exception {
        final DocumentConverter converter = new DocumentConverter(context, "multi",
                "function(doc) {var ret = new Array(); ret.push(new Document()); ret.push(new Document()); return ret;}");
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), new JSONObject(), null);
        assertThat(result.length, is(2));
        assertThat(result[0].get("_id"), is("hello"));
        assertThat(result[1].get("_id"), is("hello"));
    }

    @Test
    public void testNullReturn() throws Exception {
        final DocumentConverter converter = new DocumentConverter(context, "null", "function(doc) {return null;}");
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), new JSONObject(), null);
        assertThat(result.length, is(0));
    }

    @Test
    public void testUndefinedReturn() throws Exception {
        final DocumentConverter converter = new DocumentConverter(context, "null", "function(doc) {return doc.nope;}");
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), new JSONObject(), null);
        assertThat(result.length, is(0));
    }

    @Test
    public void testRuntimeException() throws Exception {
        final DocumentConverter converter = new DocumentConverter(context, "null", "function(doc) {throw {bad : \"stuff\"}}");
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), new JSONObject(), null);
        assertThat(result.length, is(0));
    }

    @Test
    public void testNullAddsAreIgnored() throws Exception {
        final DocumentConverter converter = new DocumentConverter(context, "null",
                "function(doc) {var ret=new Document(); ret.add(doc.nope); return ret;}");
        final Document[] result = converter.convert(doc("{_id:\"hello\"}"), new JSONObject(), null);
        assertThat(result.length, is(1));
    }

    private JSONObject doc(final String json) {
        return JSONObject.fromObject(json);
    }

}
