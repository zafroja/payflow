package com.payflow.lab;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Two threads transfer money in opposite directions (A->B and B->A) at the same time.
 * Locking "from" then "to" can deadlock; locking in a global order (by account number)
 * cannot. account-service uses the same idea with SELECT ... FOR UPDATE.
 */
final class DeadlockLab {

    static final class Account {
        final String number;
        final ReentrantLock lock = new ReentrantLock();
        BigDecimal balance;

        Account(String number, BigDecimal balance) {
            this.number = number;
            this.balance = balance;
        }
    }

    private static final int TRANSFERS_PER_THREAD = 20_000;

    static void run() throws InterruptedException {
        runScenario("naive (lock from, then to)", false);
        runScenario("ordered (lock lower account number first)", true);
        System.out.println("Notice: timeouts = moments where the naive version would have deadlocked forever"
                + " with lock(); the ordered version never waits in a cycle. Total money is conserved in both.");
    }

    private static void runScenario(String label, boolean ordered) throws InterruptedException {
        Account a = new Account("PF0000000001", new BigDecimal("1000000.00"));
        Account b = new Account("PF0000000002", new BigDecimal("1000000.00"));
        AtomicInteger timeouts = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> repeat(a, b, ordered, timeouts));
        pool.submit(() -> repeat(b, a, ordered, timeouts));
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);

        System.out.printf("%-45s lock timeouts=%-6d total=%s%n",
                label, timeouts.get(), a.balance.add(b.balance));
    }

    private static void repeat(Account from, Account to, boolean ordered, AtomicInteger timeouts) {
        BigDecimal amount = new BigDecimal("1.00");
        for (int i = 0; i < TRANSFERS_PER_THREAD; i++) {
            while (!transfer(from, to, amount, ordered)) {
                timeouts.incrementAndGet(); // back off and retry
            }
        }
    }

    /** Returns false if the second lock could not be taken in time (deadlock avoided by backing off). */
    private static boolean transfer(Account from, Account to, BigDecimal amount, boolean ordered) {
        Account first = from;
        Account second = to;
        if (ordered && from.number.compareTo(to.number) > 0) {
            first = to;
            second = from;
        }
        first.lock.lock();
        try {
            if (!second.lock.tryLock(1, TimeUnit.MILLISECONDS)) {
                return false;
            }
            try {
                from.balance = from.balance.subtract(amount);
                to.balance = to.balance.add(amount);
                return true;
            } finally {
                second.lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            first.lock.unlock();
        }
    }

    private DeadlockLab() {
    }
}
