package com.github.rnewson.couchdb.lucene;

import org.json.JSONObject;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.AsyncHttpProvider;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import com.ning.http.client.providers.jdk.JDKAsyncHttpProvider;

public class Ning {

	private static class MyHandler implements AsyncHandler<Response> {

		private String url;

		public MyHandler(final String url) {
			this.url = url;
		}

		public STATE onBodyPartReceived(final HttpResponseBodyPart part) throws Exception {
			final String line = new String(part.getBodyPartBytes(), "UTF-8");
			if ("\n".equals(line)) {
				System.err.println(url + ": heartbeat");
				return STATE.CONTINUE;
			} else {
				final JSONObject json = new JSONObject(line);
				if (json.has("last_seq")) {
					System.err.println(url + ": response ended at sequence " + json.getLong("last_seq"));
					return STATE.ABORT;
				}
				if (json.has("error")) {
					System.err.println(url + ": response encountered error: " + json.getString("reason"));
					return STATE.ABORT;
				}
				System.out.println(url + ": " + json);
				return STATE.CONTINUE;
			}
		}

		public Response onCompleted() throws Exception {
			System.out.println(url + " complete");
			return null;
		}

		public STATE onHeadersReceived(final HttpResponseHeaders headers) throws Exception {
			return STATE.CONTINUE;
		}

		public STATE onStatusReceived(final HttpResponseStatus status) throws Exception {
			System.err.println(url + ": " + status.getStatusCode());
			return status.getStatusCode() == 200 ? STATE.CONTINUE : STATE.ABORT;
		}

		public void onThrowable(final Throwable t) {
			System.err.println(url + " threw;");
			t.printStackTrace();
		}

	}

	public static void main(final String[] args) throws Exception {
		AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
		for (int i = 0; i < args.length; i++) {
			asyncHttpClient.prepareGet(args[i] + "/_changes?feed=continuous&heartbeat=65000").execute(
					new MyHandler(args[i]));
		}
	}

}
