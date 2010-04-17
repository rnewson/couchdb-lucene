package com.github.rnewson.couchdb.lucene;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

public class PathParts {

	private static final Pattern QUERY_REGEX = Pattern
			.compile("^/([^/]+)/([^/]+)/_design/([^/]+)/([^/]+)/?([^/]+)?");

	private static final Pattern GLOBAL_REGEX = Pattern
			.compile("^/([^/]+)/([^/]+)/((([^/]+)))");

	private Matcher matcher;

	public PathParts(final HttpServletRequest req) {
		this(req.getRequestURI());
	}

	public PathParts(final String path) {
		matcher = QUERY_REGEX.matcher(path);
		if (!matcher.matches()) {
			matcher = GLOBAL_REGEX.matcher(path);
		}
		if (!matcher.matches()) {
			throw new IllegalArgumentException(path + " is not a valid path");
		}
	}

	public String getKey() {
		return matcher.group(1);
	}

	public String getDesignDocumentName() {
		return "_design/" + matcher.group(3);
	}

	public String getDatabaseName() {
		return matcher.group(2);
	}

	public String getViewName() {
		return matcher.group(4);
	}

	public String getCommand() {
		if (matcher.groupCount() != 5) {
			return null;
		}
		return matcher.group(5);
	}

	@Override
	public String toString() {
		return "PathParts [getCommand()=" + getCommand()
				+ ", getDatabaseName()=" + getDatabaseName()
				+ ", getDesignDocumentName()=" + getDesignDocumentName()
				+ ", getKey()=" + getKey() + ", getViewName()=" + getViewName()
				+ "]";
	}

}
