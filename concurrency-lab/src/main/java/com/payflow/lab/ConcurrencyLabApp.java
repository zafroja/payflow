package com.payflow.lab;

/**
 * Runs every lab in order. Each lab prints a short "what you should notice" line so the
 * output doubles as interview talking points.
 */
public class ConcurrencyLabApp {

    public static void main(String[] args) throws Exception {
        banner("1. Race condition vs atomic counters (ExecutorService + CountDownLatch)");
        RaceConditionLab.run();

        banner("2. Deadlock risk vs lock ordering (ReentrantLock.tryLock)");
        DeadlockLab.run();

        banner("3. Producer / consumer payment queue (BlockingQueue + poison pill)");
        PaymentQueueLab.run();

        banner("4. CompletableFuture pipeline (parallel calls, combine, timeout, fallback)");
        CompletableFutureLab.run();

        banner("5. Java 8 streams, Optional and functional interfaces on transactions");
        Java8StreamsLab.run();
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("==== " + title + " ====");
    }
}
