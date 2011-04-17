package com.github.rnewson.couchdb.lucene.pojo;

import org.codehaus.jackson.map.ObjectMapper;

final class Jackson {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	static final ObjectMapper getObjectMapper() {
		return OBJECT_MAPPER;
	}

}
