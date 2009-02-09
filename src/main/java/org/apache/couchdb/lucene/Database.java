package org.apache.couchdb.lucene;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.io.IOUtils;

/**
 * Communication with couchdb.
 * 
 * @author rnewson
 * 
 */
public final class Database {

	private static final HttpClient CLIENT = new HttpClient();

	private static final String[] EMPTY_ARR = new String[0];

	static {
		CLIENT.getParams().setCookiePolicy(CookiePolicy.IGNORE_COOKIES);
	}

	private final String url;

	public Database(final String url) {
		if (url.endsWith("/"))
			this.url = url.substring(0, url.length() - 1);
		else
			this.url = url;
	}

	public String[] getAllDatabases() throws HttpException, IOException {
		return (String[]) JSONArray.fromObject(get("_all_dbs")).toArray(EMPTY_ARR);
	}

	public JSONObject getAllDocsBySeq(final String dbname, final long from, final int limit) throws HttpException,
			IOException {
		return JSONObject.fromObject(get(String.format("%s/_all_docs_by_seq?startkey=%s&limit=%d&include_docs=true",
				dbname, from, limit)));
	}

	public JSONObject getDoc(final String dbname, final String id, final String rev) throws HttpException, IOException {
		return JSONObject.fromObject(get(String.format("%s/%s?rev=%s", dbname, id, rev)));
	}

	public DbInfo getInfo(final String dbname) throws HttpException, IOException {
		return new DbInfo(JSONObject.fromObject(get(dbname)));
	}

	private String get(final String path) throws HttpException, IOException {
		final GetMethod get = new GetMethod(url(path));
		try {
			CLIENT.executeMethod(get);
			final InputStream in = get.getResponseBodyAsStream();
			try {
				final StringWriter writer = new StringWriter();
				IOUtils.copy(in, writer, "UTF-8");
				return writer.toString();
			} finally {
				in.close();
			}
		} finally {
			get.releaseConnection();
		}
	}

	private String url(final String path) {
		return String.format("%s/%s", url, path);
	}

	private int post(final String path, final String body) throws HttpException, IOException {
		final PostMethod post = new PostMethod(url(path));
		post.setRequestEntity(new StringRequestEntity(body, "application/json", "UTF-8"));
		try {
			return CLIENT.executeMethod(post);
		} finally {
			post.releaseConnection();
		}
	}

}
