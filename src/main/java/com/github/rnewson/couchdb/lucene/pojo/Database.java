package com.github.rnewson.couchdb.lucene.pojo;

import java.io.IOException;
import java.util.List;

import org.apache.http.client.HttpClient;

import com.github.rnewson.couchdb.lucene.couchdb.HttpUtils;
import com.github.rnewson.couchdb.lucene.util.Utils;

public final class Database {
	
	private final HttpClient httpClient;

	private final String url;

	public Database(final HttpClient httpClient, final String url) {
		this.httpClient = httpClient;
		this.url = url.endsWith("/") ? url : url + "/";
	}

	public boolean create() throws IOException {
		return HttpUtils.put(httpClient, url, null) == 201;
	}

	public boolean delete() throws IOException {
		return HttpUtils.delete(httpClient, url) == 200;
	}
	
	public List<DesignDocument> getAllDesignDocuments() throws IOException {
		final String body = HttpUtils.get(httpClient, String
				.format("%s_all_docs?startkey=%s&endkey=%s&include_docs=true",
						url, Utils.urlEncode("\"_design\""), Utils
								.urlEncode("\"_design0\"")));
		return Jackson.getObjectMapper().readValue(body, List<DesignDocument>.class);
	}

}
