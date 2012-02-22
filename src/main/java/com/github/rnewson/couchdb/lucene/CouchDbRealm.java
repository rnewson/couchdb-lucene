package com.github.rnewson.couchdb.lucene;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;

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
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.security.UserRealm;

public class CouchDbRealm implements UserRealm {

	private class CouchDbPrincipal implements Principal {

		private final String name;
		private final JSONArray roles;

		public CouchDbPrincipal(final String name, final JSONArray roles) {
			this.name = name;
			this.roles = roles;
		}

		public String getName() {
			return name;
		}

		public boolean isInRole(final String role) {
			for (int i = 0; i < roles.length(); i++) {
				try {
					if (role.equals(roles.getString(i)))
						return true;
				} catch (JSONException e) {
					// ignored.
				}
			}
			return false;
		}

	}

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
		final String sectionName = new PathParts(req).getKey();
		final Configuration section = ini.getSection(sectionName);
		if (!section.containsKey("url")) {
			return null;
		}

		String url = section.getString("url");
		url = url.endsWith("/") ? url : url + "/";

		final HttpGet get = new HttpGet(url + "_session");
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
						final String body = EntityUtils.toString(response
								.getEntity());
						try {
							final JSONObject json = new JSONObject(body);
							final JSONObject userCtx = json
									.getJSONObject("userCtx");
							final JSONArray roles = userCtx
									.getJSONArray("roles");
							return new CouchDbPrincipal(username, roles);
						} catch (final JSONException e) {
							return null;
						}						
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
		return ((CouchDbPrincipal) user).isInRole(role);
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
