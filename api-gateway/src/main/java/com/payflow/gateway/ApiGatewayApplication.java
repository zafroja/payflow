package com.payflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single entry point for all clients. Responsibilities:
 * <ul>
 *   <li>Routing to services discovered through Eureka (lb://service-name)</li>
 *   <li>JWT validation, so downstream services only receive authenticated calls</li>
 *   <li>Per-user rate limiting backed by Redis</li>
 * </ul>
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
