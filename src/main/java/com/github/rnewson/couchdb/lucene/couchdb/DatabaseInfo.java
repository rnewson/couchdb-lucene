package com.github.rnewson.couchdb.lucene.couchdb;

import org.json.JSONException;
import org.json.JSONObject;

public class DatabaseInfo {

	private final JSONObject json;

	public DatabaseInfo(final JSONObject json) {
		this.json = json;
	}

	public UpdateSequence getUpdateSequence() throws JSONException {
		return UpdateSequence.parseUpdateSequence(json.getString("update_seq"));
	}

	public String getName() throws JSONException {
		return json.getString("db_name");
	}

}
