package com.github.rnewson.cl;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

@Provider
@Produces({ "application/json", "text/plain" })
public final class JsonWriter implements MessageBodyWriter<JsonNode> {

    @Override
    public long getSize(final JsonNode t, final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return -1L;
    }

    @Override
    public boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return JsonNode.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(final JsonNode t, final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders, final OutputStream entityStream)
            throws IOException, WebApplicationException {
        new ObjectMapper().writeValue(entityStream, t);
    }

}
