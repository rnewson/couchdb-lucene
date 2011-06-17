package com.github.rnewson.cl;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

public class CouchException extends WebApplicationException {

    public static class DatabaseAlreadyExistsException extends CouchException {

        private static final long serialVersionUID = 1L;

        public DatabaseAlreadyExistsException() {
            super(Status.PRECONDITION_FAILED, "file_exists", "The database could not be "
                    + "created, the file already exists.");
        }
    }

    private static final long serialVersionUID = 1L;

    private final String error;

    private final String reason;

    public CouchException(final Status status, final String error, final String reason) {
        super(status);
        this.error = error;
        this.reason = reason;
    }

    public String getError() {
        return error;
    }

    public String getReason() {
        return reason;
    }

}
