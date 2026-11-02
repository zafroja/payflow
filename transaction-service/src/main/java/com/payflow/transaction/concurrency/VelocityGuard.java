package com.payflow.transaction.concurrency;

import com.payflow.transaction.config.TransferProperties;
import com.payflow.transaction.exception.Errors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Limits how many transfers one user can start per minute, across ALL pods.
 * An in-memory counter would only see one pod's traffic; Redis INCR is atomic and shared.
 *
 * Fixed one-minute window: key velocity:{userId}:{epochMinute}, expired after two minutes.
 */
@Component
public class VelocityGuard {

    private static final Logger log = LoggerFactory.getLogger(VelocityGuard.class);

    private final StringRedisTemplate redis;
    private final TransferProperties properties;
    private final Clock clock;

    @Autowired
    public VelocityGuard(StringRedisTemplate redis, TransferProperties properties) {
        this(redis, properties, Clock.systemUTC());
    }

    VelocityGuard(StringRedisTemplate redis, TransferProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    public void check(Long userId) {
        long minute = clock.instant().getEpochSecond() / 60;
        String key = "payflow:velocity:" + userId + ":" + minute;
        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(2));
            }
        } catch (DataAccessException e) {
            // Product decision: fail open. Balances are still protected by account-service;
            // this guard is an extra fraud control, not the source of truth.
            log.warn("Velocity check skipped, Redis unavailable: {}", e.getMessage());
            return;
        }
        if (count != null && count > properties.maxPerMinute()) {
            throw Errors.tooManyTransfers(properties.maxPerMinute());
        }
    }
}
