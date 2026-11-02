package com.payflow.transaction.service;

import com.payflow.transaction.api.dto.TransferResponse;
import com.payflow.transaction.api.dto.TransferSummary;
import com.payflow.transaction.api.dto.TransferSummary.RecipientTotal;
import com.payflow.transaction.config.TransferProperties;
import com.payflow.transaction.domain.Transfer;
import com.payflow.transaction.domain.TransferStatus;
import com.payflow.transaction.exception.Errors;
import com.payflow.transaction.repository.TransferRepository;
import com.payflow.transaction.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spending summary built with Java 8 streams and collectors (groupingBy, reducing, counting,
 * Optional, java.time). Money totals use BigDecimal reductions, never double sums.
 */
@Service
public class StatementService {

    private final TransferRepository transfers;
    private final TransferProperties properties;

    public StatementService(TransferRepository transfers, TransferProperties properties) {
        this.transfers = transfers;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public TransferSummary summary(AuthenticatedUser user, int days) {
        if (days < 1 || days > 365) {
            throw Errors.invalid("days must be between 1 and 365");
        }
        ZoneId zone = ZoneId.of(properties.reportingZone());
        Instant since = LocalDate.now(zone).minusDays(days - 1L).atStartOfDay(zone).toInstant();
        List<Transfer> all = transfers.findByInitiatedByAndCreatedAtAfter(user.userId(), since);

        Map<TransferStatus, Long> countByStatus = all.stream()
                .collect(Collectors.groupingBy(Transfer::getStatus,
                        () -> new EnumMap<>(TransferStatus.class), Collectors.counting()));

        List<Transfer> completed = all.stream()
                .filter(t -> t.getStatus() == TransferStatus.COMPLETED)
                .collect(Collectors.toList());

        BigDecimal totalSent = completed.stream()
                .map(Transfer::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageSent = completed.isEmpty()
                ? BigDecimal.ZERO
                : totalSent.divide(BigDecimal.valueOf(completed.size()), 2, RoundingMode.HALF_EVEN);

        Function<Transfer, LocalDate> localDay = t -> t.getCreatedAt().atZone(zone).toLocalDate();
        Map<LocalDate, BigDecimal> dailySent = completed.stream()
                .collect(Collectors.groupingBy(localDay, TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Transfer::getAmount, BigDecimal::add)));

        List<RecipientTotal> topRecipients = completed.stream()
                .collect(Collectors.groupingBy(Transfer::getToAccount))
                .entrySet().stream()
                .map(e -> new RecipientTotal(e.getKey(),
                        e.getValue().stream().map(Transfer::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().size()))
                .sorted(Comparator.comparing(RecipientTotal::total).reversed())
                .limit(3)
                .collect(Collectors.toList());

        TransferResponse largest = completed.stream()
                .max(Comparator.comparing(Transfer::getAmount))
                .map(TransferResponse::from)
                .orElse(null);

        return new TransferSummary(days, zone.getId(), all.size(), countByStatus, totalSent, averageSent,
                dailySent, topRecipients, largest);
    }
}
