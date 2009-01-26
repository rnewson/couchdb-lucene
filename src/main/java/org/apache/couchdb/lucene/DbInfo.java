package org.apache.couchdb.lucene;

import net.sf.json.JSONObject;

public class DbInfo {

	private final JSONObject obj;

	DbInfo(final JSONObject obj) {
		this.obj = obj;
	}

	long getUpdateSeq() {
		return obj.getLong("update_seq");
	}

	long getDocCount() {
		return obj.getLong("doc_count");
	}

	long getDocDeletedCount() {
		return obj.getLong("doc_del_count");
	}

	boolean isCompactRunning() {
		return obj.getBoolean("compact_running");
	}

}
