package com.github.rnewson.couchdb.lucene;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class CouchDbRegistry {

    private final Map<String, String> map = new HashMap<String, String>();

    public CouchDbRegistry(final Properties properties, final String prefix) {
        for (final String name : properties.stringPropertyNames()) {
            if (name.startsWith(prefix)) {
                map.put(name.substring(prefix.length()), properties.getProperty(name));
            }
        }
    }

    public synchronized void register(final String key, final String url) {
        map.put(key, url.endsWith("/") ? url : url + "/");
    }

    public synchronized String url(final String key, final String path) {
        final String url = map.get(key);
        if (url == null)
            return null;
        return key + path;
    }
}
