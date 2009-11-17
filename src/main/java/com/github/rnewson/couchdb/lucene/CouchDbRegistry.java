package com.github.rnewson.couchdb.lucene;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

public final class CouchDbRegistry {

    private static final Logger LOG = Logger.getLogger(CouchDbRegistry.class);

    private final Map<String, String> map = new HashMap<String, String>();

    public CouchDbRegistry(final Properties properties, final String prefix) {
        for (final String name : properties.stringPropertyNames()) {
            if (name.startsWith(prefix)) {
                register(name.substring(prefix.length()), properties.getProperty(name));
            }
        }
        LOG.info(map);
    }

    public synchronized void register(final String key, final String url) {
        map.put(key, url.endsWith("/") ? url : url + "/");
    }

    public synchronized String url(final String key, final String path) {
        final String url = map.get(key);
        if (url == null)
            return null;
        return url + path;
    }

}
