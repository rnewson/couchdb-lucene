package com.github.rnewson.couchdb.lucene;

import javax.servlet.http.HttpServletRequest;

@Deprecated
public class IndexKey {

	private final String key;

	private final String database;

	private final String ddoc;

	private final String view;

	public static IndexKey parse(final HttpServletRequest req) {
		final String[] parts = req.getRequestURI().replaceFirst("/", "").split(
				"/");
		if (parts.length < 4) {
			return null;
		}
		return new IndexKey(parts[0], parts[1], parts[2], parts[3]);
	}

	public IndexKey(String key, String database, String ddoc, String view) {
		this.key = key;
		this.database = database;
		this.ddoc = ddoc;
		this.view = view;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((database == null) ? 0 : database.hashCode());
		result = prime * result + ((ddoc == null) ? 0 : ddoc.hashCode());
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		result = prime * result + ((view == null) ? 0 : view.hashCode());
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
		IndexKey other = (IndexKey) obj;
		if (database == null) {
			if (other.database != null)
				return false;
		} else if (!database.equals(other.database))
			return false;
		if (ddoc == null) {
			if (other.ddoc != null)
				return false;
		} else if (!ddoc.equals(other.ddoc))
			return false;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		if (view == null) {
			if (other.view != null)
				return false;
		} else if (!view.equals(other.view))
			return false;
		return true;
	}

}
