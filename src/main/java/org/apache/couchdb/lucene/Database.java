package org.apache.couchdb.lucene;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
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

	static final HttpClient CLIENT = new HttpClient();

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
				encode(dbname), from, limit)));
	}

	public JSONObject getDoc(final String dbname, final String id, final String rev) throws HttpException, IOException {
		if (rev == null)
			return JSONObject.fromObject(get(String.format("%s/%s", encode(dbname), id)));
		else
			return JSONObject.fromObject(get(String.format("%s/%s?rev=%s", encode(dbname), id, rev)));
	}

	public JSONObject getDocs(final String dbname, final String... ids) throws HttpException, IOException {
		final JSONArray keys = new JSONArray();
		for (final String id : ids) {
			keys.add(id);
		}
		final JSONObject req = new JSONObject();
		req.element("keys", keys);

		return JSONObject.fromObject(post(String.format("%s/_all_docs?include_docs=true", encode(dbname)), req.toString()));
	}

	public JSONObject getInfo(final String dbname) throws HttpException, IOException {
		return JSONObject.fromObject(get(encode(dbname)));
	}

	private String get(final String path) throws HttpException, IOException {
		return execute(new GetMethod(url(path)));
	}

	String url(final String path) {
		return String.format("%s/%s", url, path);
	}

	String encode(final String path) {
		try {
			return URLEncoder.encode(path, "UTF-8");
		} catch (final UnsupportedEncodingException e) {
			throw new Error("UTF-8 support missing!");
		}
	}

	private String post(final String path, final String body) throws HttpException, IOException {
		final PostMethod post = new PostMethod(url(path));
		post.setRequestEntity(new StringRequestEntity(body, "application/json", "UTF-8"));
		return execute(post);
	}

	private synchronized String execute(final HttpMethodBase method) throws HttpException, IOException {
		try {
			CLIENT.executeMethod(method);
			final InputStream in = method.getResponseBodyAsStream();
			try {
				final StringWriter writer = new StringWriter(2048);
				IOUtils.copy(in, writer, method.getResponseCharSet());
				return writer.toString();
			} finally {
				in.close();
			}
		} finally {
			method.releaseConnection();
		}
	}

}
