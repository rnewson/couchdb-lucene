package com.github.rnewson.couchdb.lucene;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IdempotentExecutorTest {

    private class MyRunnable implements Runnable {

        private boolean ran = false;
        private final CountDownLatch latch = new CountDownLatch(1);

        public void run() {
            try {
                latch.await();
                ran = true;
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        public void countdown() {
            latch.countDown();
        }

    }

    private IdempotentExecutor<Integer> executor;

    @Before
    public void setup() {
        executor = new IdempotentExecutor<Integer>();
    }

    @After
    public void teardown() {
        executor.shutdownNow();
    }

    @Test
    public void testRunTaskToCompletion() throws Exception {
        final MyRunnable r = new MyRunnable();
        r.countdown();
        executor.submit(0, r);
        Thread.sleep(10);
        assertThat(r.ran, is(true));
        assertThat(executor.getTaskCount(), is(0));
    }

    @Test
    public void testIdempotency() {
        final MyRunnable r1 = new MyRunnable();
        final MyRunnable r2 = new MyRunnable();
        executor.submit(0, r1);
        executor.submit(0, r2);
        assertThat(executor.getTaskCount(), is(1));
    }

    @Test
    public void testConcurrency() {
        final MyRunnable r1 = new MyRunnable();
        final MyRunnable r2 = new MyRunnable();
        executor.submit(0, r1);
        executor.submit(1, r2);
        assertThat(executor.getTaskCount(), is(2));
    }
    
    @Test
    public void testShutdown() {
        final MyRunnable r1 = new MyRunnable();
        final MyRunnable r2 = new MyRunnable();
        executor.submit(0, r1);
        executor.submit(1, r2);
        assertThat(executor.getTaskCount(), is(2));
        executor.shutdownNow();
        assertThat(executor.getTaskCount(), is(0));        
    }

}
