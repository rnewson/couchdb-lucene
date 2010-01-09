package com.github.rnewson.couchdb.lucene.util;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

public final class IndexPath {

    public static IndexPath parse(final HierarchicalINIConfiguration configuration, final HttpServletRequest req) {
        final String uri = req.getRequestURI().replaceFirst("^/\\w+/", "");
        final String[] parts = uri.split("/");
        if (parts.length != 4) {
            return null;
        }
        final Configuration section = configuration.getSection(parts[0]);
        return section.containsKey("url") ? new IndexPath(section.getString("url"), parts[1], parts[2], parts[3]) : null;
    }

    private final String database;
    private final String designDocumentName;
    private final String url;
    private final String viewName;

    public IndexPath(final String url, final String database, final String designDocumentName, final String viewName) {
        this.url = url;
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
        if (!url.equals(other.url)) {
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

    public String getUrl() {
        return url;
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
        result = prime * result + url.hashCode();
        result = prime * result + viewName.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return url + "/" + database + "/" + designDocumentName + "/" + viewName;
    }

}
