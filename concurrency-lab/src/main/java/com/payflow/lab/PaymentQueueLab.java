package com.payflow.lab;

import java.math.BigDecimal;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A bounded queue between an API thread (producer) and payment workers (consumers).
 * The bound gives back-pressure: when workers fall behind, put() blocks the producer
 * instead of the JVM running out of memory.
 */
final class PaymentQueueLab {

    record Payment(int id, BigDecimal amount) {
    }

    private static final Payment POISON_PILL = new Payment(-1, BigDecimal.ZERO);
    private static final int WORKERS = 3;
    private static final int PAYMENTS = 30;

    static void run() throws InterruptedException {
        BlockingQueue<Payment> queue = new ArrayBlockingQueue<>(5);
        AtomicInteger processed = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(WORKERS);

        for (int w = 0; w < WORKERS; w++) {
            workers.submit(() -> {
                try {
                    while (true) {
                        Payment p = queue.take();
                        if (p == POISON_PILL) {
                            return;
                        }
                        Thread.sleep(5); // simulate calling the core banking system
                        processed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        long start = System.nanoTime();
        for (int i = 1; i <= PAYMENTS; i++) {
            queue.put(new Payment(i, BigDecimal.valueOf(i * 10L)));
        }
        for (int w = 0; w < WORKERS; w++) {
            queue.put(POISON_PILL); // one pill per worker for a clean shutdown
        }
        workers.shutdown();
        workers.awaitTermination(30, TimeUnit.SECONDS);

        System.out.printf("processed %d/%d payments with %d workers in %d ms%n",
                processed.get(), PAYMENTS, WORKERS, (System.nanoTime() - start) / 1_000_000);
        System.out.println("Notice: queue capacity is 5, so the producer was throttled by put(): that is back-pressure.");
    }

    private PaymentQueueLab() {
    }
}
