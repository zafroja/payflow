package com.payflow.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Runs before every route. Validates the bearer token and forwards the caller's identity
 * to downstream services as trusted headers. Client-supplied identity headers are always
 * stripped first so nobody can spoof another user by sending X-User-Id themselves.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String ROLES = "X-User-Roles";
    public static final String CORRELATION_ID = "X-Correlation-Id";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final List<String> OPEN_PATHS = List.of("/api/auth/");

    private final JwtValidator jwtValidator;

    public JwtAuthenticationFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID) != null
                ? request.getHeaders().getFirst(CORRELATION_ID)
                : UUID.randomUUID().toString();

        ServerHttpRequest.Builder builder = request.mutate().headers(h -> {
            h.remove(USER_ID);
            h.remove(USERNAME);
            h.remove(ROLES);
            h.set(CORRELATION_ID, correlationId);
        });

        if (OPEN_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange, "Missing bearer token");
        }

        try {
            Claims claims = jwtValidator.parse(authHeader.substring(7));
            Object roles = claims.get("roles");
            String rolesCsv = roles instanceof Collection<?> c
                    ? String.join(",", c.stream().map(String::valueOf).toList())
                    : String.valueOf(roles);

            builder.header(USER_ID, String.valueOf(claims.get("uid")))
                   .header(USERNAME, claims.getSubject())
                   .header(ROLES, rolesCsv);

            return chain.filter(exchange.mutate().request(builder.build()).build());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected token on {}: {}", path, e.getMessage());
            return reject(exchange, "Invalid or expired token");
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    /** Must run before the route-level rate limiter so the limiter can key on X-User-Id. */
    @Override
    public int getOrder() {
        return -100;
    }
}
