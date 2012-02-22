package com.github.rnewson.couchdb.lucene;

import java.io.IOException;
import java.security.Principal;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.BasicUserPrincipal;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.protocol.HTTP;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.security.UserRealm;

public class CouchDbRealm implements UserRealm {

	private final HttpClient client;

	private final HierarchicalINIConfiguration ini;

	public CouchDbRealm(final HttpClient client,
			final HierarchicalINIConfiguration ini) {
		this.client = client;
		this.ini = ini;
	}

	public String getName() {
		return "COUCHDB";
	}

	public Principal getPrincipal(String username) {
		return null;
	}

	public Principal authenticate(final String username,
			final Object credentials, final Request req) {
		final PathParts parts = new PathParts(req);
		final String sectionName = parts.getKey();
		final Configuration section = ini.getSection(sectionName);
		if (!section.containsKey("url")) {
			return null;
		}

		String url = section.getString("url");
		url = url.endsWith("/") ? url : url + "/";

		final HttpGet get = new HttpGet(url + parts.getDatabaseName());
		final UsernamePasswordCredentials creds = new UsernamePasswordCredentials(
				username, (String) credentials);
		final Header auth = BasicScheme.authenticate(creds,
				HTTP.DEFAULT_PROTOCOL_CHARSET, false);
		get.addHeader(auth);

		try {
			return client.execute(get, new ResponseHandler<Principal>() {

				public Principal handleResponse(final HttpResponse response)
						throws ClientProtocolException, IOException {
					if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
						return new BasicUserPrincipal(username);
					}
					return null;
				}
			});
		} catch (final ClientProtocolException e) {
			return null;
		} catch (final IOException e) {
			return null;
		}
	}

	public boolean reauthenticate(Principal user) {
		return true;
	}

	public boolean isUserInRole(Principal user, String role) {
		return true;
	}

	public void disassociate(Principal user) {
	}

	public Principal pushRole(Principal user, String role) {
		return null;
	}

	public Principal popRole(Principal user) {
		return null;
	}

	public void logout(Principal user) {
	}

}
