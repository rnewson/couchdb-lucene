package com.github.rnewson.cl;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;

import org.apache.lucene.document.Document;

@Provider
public class BulkDocsReader implements MessageBodyReader<List<Document>> {

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        if (genericType instanceof ParameterizedType) {
            final ParameterizedType parameterizedType = (ParameterizedType) genericType;
            return List.class == parameterizedType.getRawType()
                    && Document.class == parameterizedType.getActualTypeArguments()[0];
        }
        return false;
    }

    @Override
    public List<Document> readFrom(Class<List<Document>> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException, WebApplicationException {
        throw new UnsupportedOperationException("readFrom not supported!");
    }

}
