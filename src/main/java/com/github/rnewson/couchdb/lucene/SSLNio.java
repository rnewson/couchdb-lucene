package com.github.rnewson.couchdb.lucene;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class SSLNio {
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

	public static void main(String[] args) throws Exception {
		final SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, new TrustManager[] { new BlindTrust() }, null);

		final SSLEngine engine = context.createSSLEngine();
		engine.setUseClientMode(true);
		engine.beginHandshake();

		SSLSession session = engine.getSession();
		int appBufferMax = session.getApplicationBufferSize();
		int netBufferMax = session.getPacketBufferSize();

		final ByteBuffer clientIn = ByteBuffer.allocate(appBufferMax + 50);
		final ByteBuffer clientOut = ByteBuffer
				.wrap("GET /db1/_changes?feed=continuous\r\n\r\n".getBytes());
		final ByteBuffer cTOs = ByteBuffer.allocate(netBufferMax);
		final ByteBuffer sTOc = ByteBuffer.allocate(netBufferMax);

		final SocketChannel channel = SocketChannel.open(new InetSocketAddress(
				"localhost", 6984));

		final Selector selector = Selector.open();
		channel.configureBlocking(false);
		channel.register(selector,
				SelectionKey.OP_READ | SelectionKey.OP_WRITE, session);

		SSLEngineResult result = null;

		while (true) {
			selector.select();
			final Set<SelectionKey> keys = selector.selectedKeys();
			for (final Iterator<SelectionKey> i = keys.iterator(); i.hasNext();) {
				final SelectionKey key = i.next();
				i.remove();

				result = engine.wrap(clientOut, cTOs);
				runDelegatedTasks(engine);
				cTOs.flip();
				channel.write(cTOs);
				cTOs.compact();

				channel.read(sTOc);
				sTOc.flip();
				result = engine.unwrap(sTOc, clientIn);
				runDelegatedTasks(engine);
				sTOc.compact();

				if (result.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING
						&& result.getStatus() == Status.OK) {
					clientIn.flip();
					final Scanner scanner = new Scanner(clientIn);
					System.out.print(decode(clientIn));
					clientIn.compact();
				}

				if (result.getStatus() == Status.CLOSED) {
					channel.close();
				}
			}
		}
	}

	private static String decode(final ByteBuffer in) throws IOException {
		final CharsetDecoder decoder = Charset.forName("US-ASCII").newDecoder();
		final CharBuffer buf = decoder.decode(in);
		return new String(buf.array());
	}

	private static void runDelegatedTasks(final SSLEngine engine) {
		if (engine.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
			Runnable runnable;
			while ((runnable = engine.getDelegatedTask()) != null) {
				runnable.run();
			}
		}
	}

}
