package com.github.rnewson.couchdb.lucene.v2;

import static java.util.concurrent.TimeUnit.MINUTES;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.nio.DefaultClientIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.entity.ConsumingNHttpEntityTemplate;
import org.apache.http.nio.entity.ContentListener;
import org.apache.http.nio.protocol.AsyncNHttpClientHandler;
import org.apache.http.nio.protocol.NHttpRequestExecutionHandler;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;

/**
 * Non-blocking consumption of _changes responses from many databases.
 * @author rnewson
 *
 */
final class Indexer2 {

    public static void main(final String[] args) throws Exception {
        HttpParams params = new BasicHttpParams();
        params.setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024).setBooleanParameter(
                CoreConnectionPNames.STALE_CONNECTION_CHECK, false).setBooleanParameter(
                CoreConnectionPNames.TCP_NODELAY, true).setParameter(CoreProtocolPNames.USER_AGENT,
                "HttpComponents/1.1");

        final ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(2, params);

        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new RequestContent());
        httpproc.addInterceptor(new RequestTargetHost());
        httpproc.addInterceptor(new RequestConnControl());
        httpproc.addInterceptor(new RequestUserAgent());
        httpproc.addInterceptor(new RequestExpectContinue());

        final AsyncNHttpClientHandler handler = new AsyncNHttpClientHandler(httpproc,
                new MyNHttpRequestExecutionHandler(), new DefaultConnectionReuseStrategy(), params);

        final IOEventDispatch ioEventDispatch = new DefaultClientIOEventDispatch(handler, params);

        final Thread t = new Thread(new Runnable() {

            public void run() {
                try {
                    ioReactor.execute(ioEventDispatch);
                } catch (InterruptedIOException ex) {
                    System.err.println("Interrupted");
                } catch (IOException e) {
                    System.err.println("I/O error: " + e.getMessage());
                }
                System.out.println("Shutdown");
            }

        });
        t.start();

        for (int i = 1; i <= 10; i++) {
            ioReactor.connect(new InetSocketAddress("localhost", 5984), null, "/db" + i + "/_changes?feed=continuous",
                    null);
        }

        MINUTES.sleep(5);

        System.out.println("Shutting down I/O reactor");
        ioReactor.shutdown();
        System.out.println("Done");
    }

    static class MyNHttpRequestExecutionHandler implements NHttpRequestExecutionHandler {

        private final static String DONE_FLAG = "done";

        @Override
        public void finalizeContext(HttpContext context) {
            context.removeAttribute(DONE_FLAG);
        }

        @Override
        public void initalizeContext(HttpContext context, Object attachment) {
            // Empty.
            context.setAttribute("path", attachment);
        }

        @Override
        public HttpRequest submitRequest(final HttpContext context) {
            // Submit HTTP GET once
            Object done = context.getAttribute(DONE_FLAG);
            if (done == null) {
                context.setAttribute(DONE_FLAG, Boolean.TRUE);
                return new BasicHttpRequest("GET", context.getAttribute("path").toString());
            } else {
                return null;
            }
        }

        public ConsumingNHttpEntity responseEntity(HttpResponse response, HttpContext context) throws IOException {
            return new ConsumingNHttpEntityTemplate(response.getEntity(), new ContinuousListener());
        }

        public void handleResponse(HttpResponse response, HttpContext context) throws IOException {
            System.out.println(response.getStatusLine());
            if (response.getEntity() != null) {
                System.out.println(EntityUtils.toString(response.getEntity()));
            }
        }

    }

    static class ContinuousListener implements ContentListener {

        private final SimpleInputBuffer buffer;
        private boolean finished;

        public ContinuousListener() {
            this.buffer = new SimpleInputBuffer(2048, new HeapByteBufferAllocator());
        }

        public void contentAvailable(final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
            this.buffer.consumeContent(decoder);
            if (decoder.isCompleted()) {
                this.finished = true;
            }
            final byte[] buf = new byte[2048];
            final int len = buffer.read(buf);
            System.out.println(new String(buf, 0, len));
        }

        public void finished() {
            finished = true;
        }

    }

}
