package com.github.rnewson.cl;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

@Consumes({"application/json", "*/*"})
@Provider
public final class JsonReader implements MessageBodyReader<JsonNode> {

    @Override
    public boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType) {
        return JsonNode.class == type;
    }

    @Override
    public JsonNode readFrom(final Class<JsonNode> type, final Type genericType, final Annotation[] annotations,
            final MediaType mediaType, final MultivaluedMap<String, String> httpHeaders, final InputStream entityStream)
            throws IOException, WebApplicationException {
        return new ObjectMapper().readTree(entityStream);
    }

}
