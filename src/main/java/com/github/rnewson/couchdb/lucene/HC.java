package com.github.rnewson.couchdb.lucene;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.nio.DefaultClientIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.ssl.SSLClientIOEventDispatch;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.protocol.AsyncNHttpClientHandler;
import org.apache.http.nio.protocol.EventListener;
import org.apache.http.nio.protocol.NHttpRequestExecutionHandler;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.nio.reactor.SessionRequestCallback;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;

public class HC {

	private static class MyConsumingNHttpEntity implements ConsumingNHttpEntity {

		private final ByteBuffer buffer = ByteBuffer.allocate(4096);

		public void consumeContent() throws IOException {
		}

		public void consumeContent(final ContentDecoder decoder,
				final IOControl ioctrl) throws IOException {
			buffer.clear();
			final int bytesRead = decoder.read(buffer);
			buffer.flip();
			final CharsetDecoder charsetDecoder = Charset.forName("US-ASCII")
					.newDecoder();
			final CharBuffer charBuffer = charsetDecoder.decode(buffer);
			System.out.println(charBuffer);
		}

		public void finish() throws IOException {
			System.err.println("finish");
		}

		public InputStream getContent() throws IOException,
				IllegalStateException {
			throw new UnsupportedOperationException("getContent not supported!");
		}

		public Header getContentEncoding() {
			throw new UnsupportedOperationException(
					"getContentEncoding not supported!");
		}

		public long getContentLength() {
			throw new UnsupportedOperationException(
					"getContentLength not supported!");
		}

		public Header getContentType() {
			throw new UnsupportedOperationException(
					"getContentType not supported!");
		}

		public boolean isChunked() {
			throw new UnsupportedOperationException("isChunked not supported!");
		}

		public boolean isRepeatable() {
			throw new UnsupportedOperationException(
					"isRepeatable not supported!");
		}

		public boolean isStreaming() {
			throw new UnsupportedOperationException(
					"isStreaming not supported!");
		}

		public void writeTo(final OutputStream outstream) throws IOException {
			throw new UnsupportedOperationException("writeTo not supported!");
		}

	}

	static class EventLogger implements EventListener {

		public void connectionClosed(final NHttpConnection conn) {
			System.out.println("Connection closed: " + conn);
		}

		public void connectionOpen(final NHttpConnection conn) {
			System.out.println("Connection open: " + conn);
		}

		public void connectionTimeout(final NHttpConnection conn) {
			System.out.println("Connection timed out: " + conn);
		}

		public void fatalIOException(final IOException ex,
				final NHttpConnection conn) {
			System.err.println("I/O error: " + ex.getMessage());
		}

		public void fatalProtocolException(final HttpException ex,
				final NHttpConnection conn) {
			System.err.println("HTTP error: " + ex.getMessage());
		}

	}

	private static class BlindTrust implements X509TrustManager {

		public void checkClientTrusted(X509Certificate[] arg0, String arg1)
				throws CertificateException {
		}

		public void checkServerTrusted(X509Certificate[] arg0, String arg1)
				throws CertificateException {
		}

		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}

	}

	static class MyHttpRequestExecutionHandler implements
			NHttpRequestExecutionHandler {

		private final static String REQUEST_SENT = "request-sent";
		private final static String RESPONSE_RECEIVED = "response-received";

		private final CountDownLatch requestCount;

		public MyHttpRequestExecutionHandler(final CountDownLatch requestCount) {
			super();
			this.requestCount = requestCount;
		}

		public void finalizeContext(final HttpContext context) {
			final Object flag = context.getAttribute(RESPONSE_RECEIVED);
			if (flag == null) {
				// Signal completion of the request execution
				requestCount.countDown();
			}
		}

		public void handleResponse(final HttpResponse response,
				final HttpContext context) {
			final HttpEntity entity = response.getEntity();
			try {
				final String content = EntityUtils.toString(entity);
				System.out.println(response.getStatusLine());
				System.out.println(content);
			} catch (final IOException ex) {
				System.err.println("I/O error: " + ex.getMessage());
			}

			context.setAttribute(RESPONSE_RECEIVED, Boolean.TRUE);

			// Signal completion of the request execution
			requestCount.countDown();
		}

		public void initalizeContext(final HttpContext context,
				final Object attachment) {
			final HttpHost targetHost = (HttpHost) attachment;
			context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, targetHost);
		}

		public ConsumingNHttpEntity responseEntity(final HttpResponse response,
				final HttpContext context) throws IOException {
			return new MyConsumingNHttpEntity();
		}

		public HttpRequest submitRequest(final HttpContext context) {
			final Object flag = context.getAttribute(REQUEST_SENT);
			if (flag == null) {
				// Stick some object into the context
				context.setAttribute(REQUEST_SENT, Boolean.TRUE);
				return new BasicHttpRequest("GET",
						"/db1/_changes?feed=continuous&heartbeat=20000");
			} else {
				// No new request to submit
				return null;
			}
		}

	}

	static class MySessionRequestCallback implements SessionRequestCallback {

		private final CountDownLatch requestCount;

		public MySessionRequestCallback(final CountDownLatch requestCount) {
			super();
			this.requestCount = requestCount;
		}

		public void cancelled(final SessionRequest request) {
			System.out.println("Connect request cancelled: "
					+ request.getRemoteAddress());
			this.requestCount.countDown();
		}

		public void completed(final SessionRequest request) {
		}

		public void failed(final SessionRequest request) {
			System.out.println("Connect request failed: "
					+ request.getRemoteAddress());
			this.requestCount.countDown();
		}

		public void timeout(final SessionRequest request) {
			System.out.println("Connect request timed out: "
					+ request.getRemoteAddress());
			this.requestCount.countDown();
		}

	}

	public static void main(final String[] args) throws Exception {
		final HttpParams params = new SyncBasicHttpParams();
		params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 30000);
		params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 30000);
		params.setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE,
				8 * 1024);
		params.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK,
				true);
		params.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true);
		params.setParameter(CoreProtocolPNames.USER_AGENT, "HttpComponents/1.1");

		final ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(2,
				params);

		final HttpProcessor httpproc = new ImmutableHttpProcessor(
				new HttpRequestInterceptor[] { new RequestContent(),
						new RequestTargetHost(), new RequestConnControl(),
						new RequestUserAgent(), new RequestExpectContinue() });

		// We are going to use this object to synchronize between the
		// I/O event and main threads
		final CountDownLatch requestCount = new CountDownLatch(1);

		final boolean ssl = true;

		final SSLContext sslcontext;
		if (ssl) {
			sslcontext = SSLContext.getInstance("TLS");
			sslcontext
					.init(null, new TrustManager[] { new BlindTrust() }, null);
		} else {
			sslcontext = null;
		}

		final NHttpClientHandler handler = new AsyncNHttpClientHandler(
				httpproc, new MyHttpRequestExecutionHandler(requestCount),
				new DefaultConnectionReuseStrategy(), params);

		final IOEventDispatch ioEventDispatch;
		if (ssl) {
			ioEventDispatch = new SSLClientIOEventDispatch(handler, sslcontext,
					params);
		} else {
			ioEventDispatch = new DefaultClientIOEventDispatch(handler, params);
		}

		final Thread t = new Thread(new Runnable() {

			public void run() {
				try {
					ioReactor.execute(ioEventDispatch);
				} catch (final InterruptedIOException ex) {
					System.err.println("Interrupted");
				} catch (final IOException e) {
					System.err.println("I/O error: " + e.getMessage());
				}
				System.out.println("Shutdown");
			}

		});
		t.start();

		final int port = ssl ? 6984 : 5984;

		ioReactor.connect(new InetSocketAddress("localhost", port), null,
				new HttpHost("localhost", port), new MySessionRequestCallback(
						requestCount));

		// Block until all connections signal
		// completion of the request execution
		requestCount.await();

		System.out.println("Shutting down I/O reactor");

		ioReactor.shutdown();

		System.out.println("Done");
	}

}
