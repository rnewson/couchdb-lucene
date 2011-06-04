package com.github.rnewson.couchdb.lucene.couchdb;

import org.json.JSONException;
import org.json.JSONObject;

public class DatabaseInfo {

	private final JSONObject json;

	public DatabaseInfo(final JSONObject json) {
		this.json = json;
	}

	public long getUpdateSequence() throws JSONException {
		return json.getLong("update_seq");
	}

	public String getName() throws JSONException {
		return json.getString("db_name");
	}

}
