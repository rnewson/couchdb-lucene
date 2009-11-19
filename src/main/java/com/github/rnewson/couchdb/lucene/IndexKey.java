package com.github.rnewson.couchdb.lucene;

import javax.servlet.http.HttpServletRequest;

/**
 * An IndexKey uniquely identifies an index.
 * 
 * @author robertnewson
 */
public final class IndexKey {

    private final String databaseName;
    private final String designDocumentName;
    private final String hostKey;
    private final String viewName;

    public IndexKey(final HttpServletRequest req) {
        this(req.getPathInfo().substring(1).split("/"));
    }

    public IndexKey(final String hostKey, final String databaseName, final String designDocumentName, final String viewName) {
        this(new String[] { hostKey, databaseName, designDocumentName, viewName });
    }

    private IndexKey(final String... args) {
        if (args.length != 4) {
            throw new IllegalArgumentException();
        }
        for (final String str : args) {
            if (str == null) {
                throw new NullPointerException();
            }
        }
        this.hostKey = args[0];
        this.databaseName = args[1];
        this.designDocumentName = args[2];
        this.viewName = args[3];
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final IndexKey other = (IndexKey) obj;
        if (!databaseName.equals(other.databaseName)) {
            return false;
        } else if (!designDocumentName.equals(other.designDocumentName)) {
            return false;
        } else if (!hostKey.equals(other.hostKey)) {
            return false;
        } else if (!viewName.equals(other.viewName)) {
            return false;
        }
        return true;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDesignDocumentName() {
        return designDocumentName;
    }

    public String getHostKey() {
        return hostKey;
    }

    public String getViewName() {
        return viewName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + databaseName.hashCode();
        result = prime * result + designDocumentName.hashCode();
        result = prime * result + hostKey.hashCode();
        result = prime * result + viewName.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuffer buffer = new StringBuffer(100);
        buffer.append(hostKey);
        buffer.append("/");
        buffer.append(databaseName);
        buffer.append("/");
        buffer.append(designDocumentName);
        buffer.append("/");
        buffer.append(viewName);
        return buffer.toString();
    }

}
