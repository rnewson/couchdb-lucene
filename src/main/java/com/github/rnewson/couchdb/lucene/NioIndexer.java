package com.github.rnewson.couchdb.lucene;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

public final class NioIndexer implements Closeable {

	private static enum ResponseState {
		START, IN_HEADERS, IN_BODY
	}

	private static class Response {
		private final String databaseName;
		private ResponseState state = ResponseState.START;
		private Scanner scanner;

		private Response(final String databaseName, final SocketChannel channel) {
			this.databaseName = databaseName;
			this.scanner = new Scanner(channel, "UTF-8");
		}
	}

	public static void main(final String[] args) throws Exception {
		final NioIndexer indexer = new NioIndexer();
		for (int i = 2; i < args.length; i++) {
			indexer.register(args[0], Integer.parseInt(args[1]), args[i], "0");
		}
		indexer.run();
	}

	private final Selector selector;
	private boolean running;

	public NioIndexer() throws IOException {
		selector = Selector.open();
		running = true;
	}

	public void close() throws IOException {
		selector.close();
		running = false;
	}

	public void run() throws IOException, JSONException {
		while (running) {
			selector.select();
			final Set<SelectionKey> keys = selector.selectedKeys();
			for (final Iterator<SelectionKey> i = keys.iterator(); i.hasNext();) {
				final SelectionKey key = i.next();
				i.remove();

				if (!key.isReadable()) {
					System.err.println("not readable");
					continue;
				}

				if (!key.isValid()) {
					System.err.println("not valid");
					continue;
				}

				final Response response = (Response) key.attachment();
				if (response.scanner.hasNextLine()) {
					final String line = response.scanner.nextLine();
					switch (response.state) {
					case START:
						System.err.println("status line: " + line);
						response.state = ResponseState.IN_HEADERS;
						continue;
					case IN_HEADERS:
						if (!line.isEmpty()) {
							System.err.println("response header: " + line);
							continue;
						} else {
							System.err.println("End of response headers.");
							response.state = ResponseState.IN_BODY;
							continue;
						}
					case IN_BODY:
						if (!line.isEmpty()) {
							final JSONObject json = new JSONObject(line);
							if (json.has("last_seq")) {
								System.err.println("Last seq detected: " + json.getLong("last_seq"));
								key.cancel();
								key.channel().close();
								continue;
							}
							if (json.has("error")) {
								System.err.println("Error detected: " + json.getString("reason"));
								key.cancel();
								key.channel().close();
								continue;
							}
							System.out.println(response.databaseName + ":" + json);
						} else {
							System.err.println("heartbeat");
						}
					}
				}
			}
		}
	}

	public void register(final String host, final int port, final String databaseName, final String since)
			throws IOException {
		final SocketChannel channel = SocketChannel.open(new InetSocketAddress(host, port));
		write(channel, "GET /%s/_changes?feed=continuous&heartbeat=30000&since=%s\r\n", databaseName, since);
		write(channel, "Host: %s:%d\r\n\r\n", host, port);
		channel.configureBlocking(false);
		channel.register(selector, SelectionKey.OP_READ, new Response(databaseName, channel));
	}

	private void write(final SocketChannel channel, final String format, final Object... args) throws IOException {
		final CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
		final String str = String.format(format, args);
		final CharBuffer buf = CharBuffer.wrap(str);
		channel.write(encoder.encode(buf));
	}

}
