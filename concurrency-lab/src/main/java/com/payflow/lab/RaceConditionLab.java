package com.payflow.lab;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 8 threads each record 100,000 "deposits of 1 cent" into a shared total.
 * The plain long loses updates because ++ is read-modify-write, not atomic.
 */
final class RaceConditionLab {

    private static final int THREADS = 8;
    private static final int OPS_PER_THREAD = 100_000;

    private static long unsafeTotal = 0;                     // broken
    private static long synchronizedTotal = 0;               // correct, but contended
    private static final Object LOCK = new Object();
    private static final AtomicLong atomicTotal = new AtomicLong();   // CAS
    private static final LongAdder adderTotal = new LongAdder();      // striped, best under contention

    static void run() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);     // release all threads at once
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        unsafeTotal++;
                        synchronized (LOCK) {
                            synchronizedTotal++;
                        }
                        atomicTotal.incrementAndGet();
                        adderTotal.increment();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        long expected = (long) THREADS * OPS_PER_THREAD;
        System.out.printf("expected      : %,d%n", expected);
        System.out.printf("plain long    : %,d  (lost %,d updates)%n", unsafeTotal, expected - unsafeTotal);
        System.out.printf("synchronized  : %,d%n", synchronizedTotal);
        System.out.printf("AtomicLong    : %,d%n", atomicTotal.get());
        System.out.printf("LongAdder     : %,d%n", adderTotal.sum());
        System.out.println("Notice: only the plain long is wrong. In the services the same problem is"
                + " solved by DB row locks, because there are many JVMs, not one.");
    }

    private RaceConditionLab() {
    }
}
