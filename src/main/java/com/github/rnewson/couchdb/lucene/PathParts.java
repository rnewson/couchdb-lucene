package com.github.rnewson.couchdb.lucene;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

public class PathParts {

	private static final Pattern REGEX = Pattern.compile("^/([^/]+)/([^/]+)/_design/([^/]+)/([^/]+)$");

	private final Matcher matcher;

	public PathParts(final HttpServletRequest req) {
		this(req.getRequestURI());
	}	

	public PathParts(final String path) {
		matcher = REGEX.matcher(path);
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
	
}
