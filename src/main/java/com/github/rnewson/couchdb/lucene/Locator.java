package com.github.rnewson.couchdb.lucene;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;

/**
 * Locate indexes by path via their signature.
 * 
 * @author robertnewson
 * 
 */
public final class Locator {

    private final Logger logger = Logger.getLogger(Locator.class);

    /**
     * Maps a path like ../<design document name>/<view name>?<args> to a view
     * signature.
     */
    private final Map<String, ViewSignature> map = new HashMap<String, ViewSignature>();

    public ViewSignature lookup(final HttpServletRequest req) {
        final String[] path = req.getPathInfo().split("/");
        if (path.length != 3) {
            return null;
        }
        return lookup(path);
    }

    public ViewSignature lookup(final String databaseName, final String designDocumentName, final String viewName) {
        return lookup(path(databaseName, designDocumentName, viewName));
    }

    public ViewSignature lookup(final String path) {
        synchronized (map) {
            return map.get(path);
        }
    }

    public Collection<String> lookupAll(final String databaseName) {
        final Set<String> result = new HashSet<String>();
        synchronized (map) {
            for (final String path : map.keySet()) {
                if (path.startsWith(databaseName + "/")) {
                    result.add(path);
                }
            }
        }
        return result;
    }

    public ViewSignature update(final String databaseName, final String designDocumentName, final String viewName,
            final String viewFunction) {
        final ViewSignature viewSignature = new ViewSignature(databaseName, viewFunction);
        synchronized (map) {
            final String path = path(databaseName, designDocumentName, viewName);
            map.put(path, viewSignature);
            logger.debug("Mapped " + path + " to " + viewSignature);
        }
        return viewSignature;
    }

    private ViewSignature lookup(final String[] pathComponents) {
        if (pathComponents.length != 3) {
            throw new IllegalArgumentException("bad path.");
        }
        return lookup(pathComponents[0], pathComponents[1], pathComponents[2]);
    }

    private String path(final String databaseName, final String designDocumentName, final String viewName) {
        return String.format("%s/%s/%s", databaseName, designDocumentName, viewName);
    }

}
