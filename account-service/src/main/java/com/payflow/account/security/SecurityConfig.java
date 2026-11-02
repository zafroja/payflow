package com.payflow.account.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
@EnableMethodSecurity   // enables @PreAuthorize on controller/service methods
public class SecurityConfig {

    private final byte[] internalApiKey;

    public SecurityConfig(@Value("${payflow.internal.api-key}") String internalApiKey) {
        this.internalApiKey = internalApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(new GatewayHeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        // Service-to-service API: never routed by the gateway, and needs a shared key
                        .requestMatchers("/internal/**").access((authentication, context) ->
                                new AuthorizationDecision(hasValidInternalKey(
                                        context.getRequest().getHeader("X-Internal-Key"))))
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    /** Constant-time comparison so the key cannot be guessed byte by byte from response timing. */
    private boolean hasValidInternalKey(String presented) {
        return presented != null
                && MessageDigest.isEqual(internalApiKey, presented.getBytes(StandardCharsets.UTF_8));
    }
}
