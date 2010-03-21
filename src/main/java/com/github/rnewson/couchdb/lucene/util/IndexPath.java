package com.github.rnewson.couchdb.lucene.util;

/**
 * Copyright 2009 Robert Newson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

public final class IndexPath {

    public static IndexPath parse(final HierarchicalINIConfiguration configuration, final HttpServletRequest req) {
        final String[] parts = parts(req);
        if (parts.length < 4) {
            return null;
        }
        final String url = url(configuration, parts[0]);
        return url == null ? null : new IndexPath(url, parts[1], parts[2], parts[3]);
    }

    public static String[] parts(final HttpServletRequest req) {
        return req.getRequestURI().replaceFirst("/", "").split("/");
    }

    public static String url(final HierarchicalINIConfiguration configuration, final String key) {
        final Configuration section = configuration.getSection(key);
        return section.containsKey("url") ? section.getString("url") : null;
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
