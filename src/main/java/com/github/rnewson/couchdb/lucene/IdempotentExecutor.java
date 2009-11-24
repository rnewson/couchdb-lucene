package com.github.rnewson.couchdb.lucene;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Prevents the same task executing concurrently.
 * 
 * @author robertnewson
 * 
 */
public final class IdempotentExecutor<K> {

    private final Map<K, Thread> tasks = new HashMap<K, Thread>();

    public synchronized boolean submit(final K key, final Runnable runnable) {
        cleanup();
        if (tasks.containsKey(key)) {
            return false;
        }

        final Thread thread = new Thread(runnable, key.toString());
        tasks.put(key, thread);
        thread.start();
        return true;
    }

    public synchronized int getTaskCount() {
        cleanup();
        return tasks.size();
    }

    public synchronized void shutdownNow() {
        for (final Thread thread : tasks.values()) {
            thread.interrupt();
        }
        tasks.clear();
    }

    private void cleanup() {
        assert Thread.holdsLock(this);
        final Iterator<Thread> it = tasks.values().iterator();
        while (it.hasNext()) {
            if (!it.next().isAlive())
                it.remove();
        }
    }

}
