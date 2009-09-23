package com.github.rnewson.couchdb.lucene.v2;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds the mapping between view names and their locations.
 * @author robertnewson
 *
 */
public final class ViewMapper {
	
	private final Map<String, File> map = new HashMap<String, File>();

	private final Database db;
	
	public ViewMapper(final Database db) {
		this.db = db;
	}
	
	public File get(final String viewName){
		ret
	}

}
