package com.github.rnewson.couchdb.lucene.couchdb;

import net.sf.json.JSONObject;

public class DatabaseInfo {

	private final JSONObject json;

	public DatabaseInfo(final JSONObject json) {
		this.json = json;
	}

	public long getUpdateSequence() {
		return json.getLong("update_seq");
	}

}
