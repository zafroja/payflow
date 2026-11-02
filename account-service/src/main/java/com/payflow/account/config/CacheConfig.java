package com.payflow.account.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.account.api.dto.AccountResponse;
import com.payflow.account.service.AccountService;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis cache for account lookups (read-heavy: every app screen shows balances).
 * <ul>
 *   <li>Values are JSON, not Java serialization: readable in redis-cli and safe across deployments</li>
 *   <li>We cache a DTO, never a JPA entity (lazy proxies do not serialize)</li>
 *   <li>A TTL bounds staleness even if an eviction is ever missed</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer accountCacheCustomizer(ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<AccountResponse> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, AccountResponse.class);

        return builder -> builder.withCacheConfiguration(AccountService.ACCOUNTS_CACHE,
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(10))
                        .disableCachingNullValues()
                        .prefixCacheNameWith("payflow:")
                        .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                        .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer)));
    }
}
