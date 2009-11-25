package com.github.rnewson.couchdb.lucene;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Prevents the same task executing concurrently.
 * 
 * @author robertnewson
 * 
 */
public final class IdempotentExecutor<K, V extends Runnable> {

    private final Map<K, Thread> threads = new HashMap<K, Thread>();
    private final Map<K, V> values = new HashMap<K, V>();

    public synchronized V submit(final K key, final V value) {
        cleanup();
        if (values.containsKey(key)) {
            return values.get(key);
        }
        final Thread thread = new Thread(value, key.toString());
        values.put(key, value);
        threads.put(key, thread);
        thread.start();
        return value;
    }

    public synchronized int getTaskCount() {
        cleanup();
        return values.size();
    }

    public synchronized void shutdownNow() {
        for (final Thread thread : threads.values()) {
            thread.interrupt();
        }
        threads.clear();
        values.clear();
    }

    private void cleanup() {
        assert Thread.holdsLock(this);
        final Iterator<Entry<K, Thread>> it = threads.entrySet().iterator();
        while (it.hasNext()) {
            final Entry<K, Thread> entry = it.next();
            if (!entry.getValue().isAlive()) {
                it.remove();
                values.remove(entry.getKey());
            }
        }
    }
}
