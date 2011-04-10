package com.github.rnewson.couchdb.lucene;

import java.util.concurrent.Future;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;

public class Ning {

	private static class MyHandler implements AsyncHandler<Response> {

		private String url;

		public MyHandler(final String url) {
			this.url = url;
		}

		public STATE onBodyPartReceived(final HttpResponseBodyPart part) throws Exception {
			System.out.print(url + ": " + new String(part.getBodyPartBytes()));
			return STATE.CONTINUE;
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
			t.printStackTrace();
		}

	}

	public static void main(final String[] args) throws Exception {
		AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
		for (int i = 0; i < args.length; i++) {
			Future<Response> f = asyncHttpClient.prepareGet(args[i] + "/_changes?feed=continuous&heartbeat=60000").execute(
					new MyHandler(args[i]));
		}
	}

}
