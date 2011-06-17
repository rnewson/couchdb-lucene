package com.github.rnewson.cl;

import java.io.IOException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public final class MyExceptionMapper implements ExceptionMapper<IOException> {

    @Override
    public Response toResponse(final IOException e) {
        return Response.status(400).build();
    }

}
