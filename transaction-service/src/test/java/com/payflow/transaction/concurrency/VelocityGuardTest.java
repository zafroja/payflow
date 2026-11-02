package com.payflow.transaction.concurrency;

import com.payflow.transaction.config.TransferProperties;
import com.payflow.transaction.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VelocityGuardTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T12:00:30Z"), ZoneOffset.UTC);
    private final TransferProperties properties = new TransferProperties(new BigDecimal("25000"), 2, 100,
            Duration.ofSeconds(10), Duration.ofSeconds(30), 10, "America/Toronto");

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private VelocityGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        guard = new VelocityGuard(redis, properties, clock);
    }

    @Test
    void blocksWhenLimitExceededInSameMinute() {
        when(ops.increment(anyString())).thenReturn(1L, 2L, 3L);

        guard.check(7L);
        guard.check(7L);
        assertThatThrownBy(() -> guard.check(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 transfers per minute");

        String expectedKey = "payflow:velocity:7:" + clock.instant().getEpochSecond() / 60;
        verify(redis, times(1)).expire(expectedKey, Duration.ofMinutes(2)); // TTL set once, on the first hit
    }

    @Test
    void failsOpenWhenRedisIsDown() {
        when(ops.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatCode(() -> guard.check(7L)).doesNotThrowAnyException();
    }
}
