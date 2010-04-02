package com.github.rnewson.couchdb.lucene;

import java.util.UUID;

import com.github.rnewson.couchdb.lucene.couchdb.View;

public class ViewKey {

	private final UUID uuid;

	private final View view;

	public ViewKey(final UUID uuid, View view) {
		this.uuid = uuid;
		this.view = view;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (uuid.hashCode());
		result = prime * result + (view.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ViewKey other = (ViewKey) obj;
		if (!uuid.equals(other.uuid))
			return false;
		if (!view.equals(other.view))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return String.format("ViewKey[uuid=%s,digest=%s]", uuid, view.getDigest());
	}

}
