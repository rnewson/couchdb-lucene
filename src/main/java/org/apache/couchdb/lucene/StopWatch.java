package org.apache.couchdb.lucene;

import java.util.HashMap;
import java.util.Map;

final class StopWatch {

	private Map<String, Long> elapsed = new HashMap<String, Long>();

	private long start = System.nanoTime();

	public void lap(final String name) {
		final long now = System.nanoTime();
		elapsed.put(name, now - start);
		start = now;
	}

	public long getElapsed(final String name) {
		return elapsed.get(name) / 1000000;
	}

}
