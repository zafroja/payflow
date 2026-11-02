package com.payflow.lab;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Builds a "portfolio value in CAD" by calling three slow dependencies in parallel.
 * Sequential: ~ 300 + 200 + 250 ms. Parallel: ~ the slowest one.
 * The FX call is slow on purpose to show timeout + fallback.
 */
final class CompletableFutureLab {

    static void run() {
        ExecutorService io = Executors.newFixedThreadPool(4); // never use the common pool for blocking I/O
        try {
            long start = System.nanoTime();

            CompletableFuture<BigDecimal> chequing = CompletableFuture.supplyAsync(() -> slow(300, "1200.50"), io);
            CompletableFuture<BigDecimal> savings = CompletableFuture.supplyAsync(() -> slow(200, "8800.00"), io);
            CompletableFuture<BigDecimal> usdFxRate = CompletableFuture
                    .supplyAsync(() -> slow(2_000, "1.39"), io)           // FX service is having a bad day
                    .completeOnTimeout(new BigDecimal("1.35"), 250, TimeUnit.MILLISECONDS) // cached fallback
                    .exceptionally(ex -> new BigDecimal("1.35"));

            CompletableFuture<BigDecimal> usdAccountInCad = CompletableFuture
                    .supplyAsync(() -> slow(150, "500.00"), io)
                    .thenCombine(usdFxRate, BigDecimal::multiply);

            BigDecimal total = CompletableFuture.allOf(chequing, savings, usdAccountInCad)
                    .thenApply(v -> List.of(chequing.join(), savings.join(), usdAccountInCad.join())
                            .stream()
                            .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .join()
                    .setScale(2, RoundingMode.HALF_EVEN);

            long ms = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("portfolio total = CAD %s in %d ms (sequential would be ~2,650 ms)%n", total, ms);

            // Fan-out / fan-in over a list, the same pattern BulkTransferService uses
            List<CompletableFuture<String>> jobs = List.of("PF01", "PF02", "PF03", "PF04").stream()
                    .map(acc -> CompletableFuture.supplyAsync(() -> acc + ":statement-ready", io))
                    .collect(Collectors.toList());
            List<String> results = jobs.stream().map(CompletableFuture::join).collect(Collectors.toList());
            System.out.println("statements: " + results);
            System.out.println("Notice: completeOnTimeout gave a fallback FX rate instead of blocking the whole request.");
        } finally {
            io.shutdownNow();
        }
    }

    private static BigDecimal slow(long millis, String value) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new BigDecimal(value);
    }

    private CompletableFutureLab() {
    }
}
