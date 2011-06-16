package com.github.rnewson.cl;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.document.NumericField;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;

@Provider
@Produces("application/json")
public final class DocumentReader implements MessageBodyReader<Document> {

    @Override
    public boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return Document.class == type;
    }

    @Override
    public Document readFrom(final Class<Document> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType, final MultivaluedMap<String, String> httpHeaders, final InputStream entityStream)
            throws IOException, WebApplicationException {
        final JsonFactory factory = new JsonFactory();
        final JsonParser parser = factory.createJsonParser(entityStream);
        try {
            final Document result = new Document();
            String parent = null;
            String fieldName = "";
            while (parser.nextToken() != null) {
                Fieldable fieldable = null;
                switch (parser.getCurrentToken()) {
                case FIELD_NAME:
                    fieldName = parent + parser.getCurrentName();
                    break;
                case START_OBJECT:
                    parent = parent == null ? fieldName : fieldName + ".";
                    break;
                case END_ARRAY:
                case END_OBJECT:
                    parent = parent.lastIndexOf(".") == -1 ? "" : parent.substring(0, parent.lastIndexOf("."));
                    break;
                case VALUE_STRING:
                    fieldable = new Field(fieldName, parser.getText(), Store.YES, Index.ANALYZED);
                    break;
                case VALUE_NUMBER_INT:
                    fieldable = new NumericField(fieldName).setIntValue(parser.getIntValue());
                    break;
                case VALUE_NUMBER_FLOAT:
                    fieldable = new NumericField(fieldName).setFloatValue(parser.getFloatValue());
                    break;
                case VALUE_TRUE:
                    fieldable = new Field(fieldName, "true", Store.YES, Index.NOT_ANALYZED);
                    break;
                case VALUE_FALSE:
                    fieldable = new Field(fieldName, "false", Store.YES, Index.NOT_ANALYZED);
                    break;
                }
                if (fieldable != null) {
                    result.add(fieldable);
                    System.out.println(fieldable);
                }
            }
            return result;
        } finally {
            parser.close();
        }
    }

}
