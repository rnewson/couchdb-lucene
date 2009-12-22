package com.github.rnewson.couchdb.lucene.util;

import javax.servlet.http.HttpServletRequest;

public final class IndexPath {

    public static IndexPath parse(final HttpServletRequest req) {
        final String uri = req.getRequestURI().replaceFirst("^/\\w+/", "");
        final String[] parts = uri.split("/");
        if (parts.length != 5) {
            return null;
        }
        try {
            return new IndexPath(parts[0], Integer.parseInt(parts[1]), parts[2], parts[3], parts[4]);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private final String database;
    private final String designDocumentName;
    private final String host;
    private final int port;

    private final String viewName;

    public IndexPath(final String host, final int port, final String database, final String designDocumentName,
            final String viewName) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.designDocumentName = designDocumentName;
        this.viewName = viewName;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof IndexPath)) {
            return false;
        }
        final IndexPath other = (IndexPath) obj;
        if (!database.equals(other.database)) {
            return false;
        }
        if (!designDocumentName.equals(other.designDocumentName)) {
            return false;
        }
        if (!host.equals(other.host)) {
            return false;
        }
        if (port != other.port) {
            return false;
        }
        if (!viewName.equals(other.viewName)) {
            return false;
        }
        return true;
    }

    public String getDatabase() {
        return database;
    }

    public String getDesignDocumentName() {
        return designDocumentName;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getViewName() {
        return viewName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + database.hashCode();
        result = prime * result + designDocumentName.hashCode();
        result = prime * result + host.hashCode();
        result = prime * result + port;
        result = prime * result + viewName.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return host + "/" + port + "/" + database + "/" + designDocumentName + "/" + viewName;
    }

}
