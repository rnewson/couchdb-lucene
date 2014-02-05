package com.github.rnewson.couchdb.lucene;

import java.io.IOException;

public class AuthorizationInfo {
	
	private String user;
	private String password;
	
	public AuthorizationInfo(String auth) throws IOException {
		if (auth.toUpperCase().startsWith("BASIC ")) { //Basic auth
			String userpassEncoded = auth.substring(6);
			sun.misc.BASE64Decoder dec = new sun.misc.BASE64Decoder();
		    String userpassDecoded = new String(dec.decodeBuffer(userpassEncoded));
		    String[] parts = userpassDecoded.split(":");
		    user = parts[0];
		    password = parts[1];
		}
	}

	public String getUser() {
		return user;
	}

	public String getPassword() {
		return password;
	}
}
