package com.payflow.gateway.config;

import com.payflow.gateway.security.JwtAuthenticationFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    /**
     * Rate-limit per authenticated user; fall back to client IP for open endpoints
     * such as /api/auth/login (protects against credential stuffing).
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.justOrEmpty(
                        exchange.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.USER_ID))
                .switchIfEmpty(Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                        .map(address -> "ip:" + address.getAddress().getHostAddress()))
                .defaultIfEmpty("anonymous");
    }
}
