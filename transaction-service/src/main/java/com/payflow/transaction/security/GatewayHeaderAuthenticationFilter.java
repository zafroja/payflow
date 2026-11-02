package com.payflow.transaction.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Turns the identity headers added by the gateway into a Spring Security Authentication,
 * so controllers can use @AuthenticationPrincipal and @PreAuthorize as usual.
 *
 * Trust model: these headers are only trustworthy because the service is not reachable from
 * outside the cluster (gateway strips client copies; Kubernetes NetworkPolicy blocks direct access).
 */
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        try {
            String userIdHeader = request.getHeader("X-User-Id");
            if (userIdHeader != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Long userId = parseLong(userIdHeader);
                if (userId != null) {
                    String roles = request.getHeader("X-User-Roles");
                    List<SimpleGrantedAuthority> authorities = Arrays.stream(roles == null ? new String[0] : roles.split(","))
                            .map(String::trim)
                            .filter(r -> !r.isEmpty())
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    AuthenticatedUser principal =
                            new AuthenticatedUser(userId, request.getHeader("X-Username"), authorities);
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, null, authorities));
                }
            }
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
