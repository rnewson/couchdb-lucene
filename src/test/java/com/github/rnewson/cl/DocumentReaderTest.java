package com.github.rnewson.cl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.lucene.document.Document;
import org.junit.Test;

public class DocumentReaderTest {

    @Test
    public void readDocument() throws Exception {
        final DocumentReader reader = new DocumentReader();
        final String json = "{\"foo\":\"bar\",\"bar\":12,\"baz\":12.5,\"bazbar\":true,"
                + "\"foobar\":[1,2,3],\"barbaz\":{\"foo\":\"buz\",\"bar\":{\"wibble\":99}}}";
        final InputStream in = new ByteArrayInputStream(json.getBytes("UTF-8"));
        reader.readFrom(Document.class, null, null, null, null, in);
    }

}
