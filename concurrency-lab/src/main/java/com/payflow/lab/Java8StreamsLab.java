package com.payflow.lab;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The Java 8 toolkit on a list of transactions: lambdas, method references, streams,
 * collectors, Optional, java.time and custom functional interfaces.
 */
final class Java8StreamsLab {

    enum Type { DEBIT, CREDIT }

    record Txn(String account, Type type, BigDecimal amount, String category, LocalDate date) {
    }

    /** A custom functional interface: a pluggable fraud rule. */
    @FunctionalInterface
    interface FraudRule {
        boolean isSuspicious(Txn txn);

        default FraudRule or(FraudRule other) {
            return t -> isSuspicious(t) || other.isSuspicious(t);
        }
    }

    static void run() {
        LocalDate today = LocalDate.of(2026, 10, 20);
        List<Txn> txns = List.of(
                new Txn("PF01", Type.CREDIT, new BigDecimal("5000.00"), "SALARY", today.minusDays(5)),
                new Txn("PF01", Type.DEBIT, new BigDecimal("1800.00"), "RENT", today.minusDays(4)),
                new Txn("PF01", Type.DEBIT, new BigDecimal("120.45"), "GROCERIES", today.minusDays(3)),
                new Txn("PF01", Type.DEBIT, new BigDecimal("64.10"), "GROCERIES", today.minusDays(1)),
                new Txn("PF01", Type.DEBIT, new BigDecimal("9500.00"), "TRANSFER", today),
                new Txn("PF02", Type.CREDIT, new BigDecimal("250.00"), "REFUND", today.minusDays(2)),
                new Txn("PF02", Type.DEBIT, new BigDecimal("45.99"), "SUBSCRIPTIONS", today.minusDays(2)));

        BinaryOperator<BigDecimal> sum = BigDecimal::add;
        Function<Txn, BigDecimal> signed = t -> t.type() == Type.CREDIT ? t.amount() : t.amount().negate();

        // 1. Net balance change per account
        Map<String, BigDecimal> netByAccount = txns.stream()
                .collect(Collectors.groupingBy(Txn::account, TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO, signed, sum)));
        System.out.println("net by account      : " + netByAccount);

        // 2. Spend per category, debits only, sorted by amount desc
        Map<String, BigDecimal> spendByCategory = txns.stream()
                .filter(t -> t.type() == Type.DEBIT)
                .collect(Collectors.groupingBy(Txn::category,
                        Collectors.reducing(BigDecimal.ZERO, Txn::amount, sum)));
        String ranked = spendByCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "[", "]"));
        System.out.println("spend by category   : " + ranked);

        // 3. Partition credits vs debits
        Map<Boolean, Long> partition = txns.stream()
                .collect(Collectors.partitioningBy(t -> t.type() == Type.CREDIT, Collectors.counting()));
        System.out.println("credits / debits    : " + partition.get(true) + " / " + partition.get(false));

        // 4. Optional: largest debit, or a safe default
        Optional<Txn> largestDebit = txns.stream()
                .filter(t -> t.type() == Type.DEBIT)
                .max(Comparator.comparing(Txn::amount));
        System.out.println("largest debit       : "
                + largestDebit.map(t -> t.category() + " " + t.amount()).orElse("none"));

        // 5. Composable fraud rules (custom functional interface + Predicate)
        FraudRule bigAmount = t -> t.amount().compareTo(new BigDecimal("5000")) > 0;
        FraudRule riskyCategory = t -> "TRANSFER".equals(t.category()) && t.type() == Type.DEBIT;
        Predicate<Txn> isToday = t -> t.date().isEqual(today);
        List<Txn> flagged = txns.stream()
                .filter(bigAmount.or(riskyCategory)::isSuspicious)
                .filter(isToday)
                .collect(Collectors.toList());
        System.out.println("flagged for review  : " + flagged.size() + " -> " + flagged.get(0).amount());
        System.out.println("Notice: transaction-service's /summary endpoint uses this same groupingBy/reducing code.");
    }

    private Java8StreamsLab() {
    }
}
