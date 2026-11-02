package com.payflow.transaction.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.transaction.exception.TransferRejectedException;
import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;

/**
 * Feign configuration for AccountClient only. Deliberately NOT annotated with @Configuration,
 * otherwise component scanning would apply these beans to every Feign client.
 */
public class AccountClientConfig {

    /** Adds the shared internal key and propagates the correlation id for end-to-end tracing. */
    @Bean
    RequestInterceptor internalAuthInterceptor(@Value("${payflow.internal.api-key}") String apiKey) {
        return template -> {
            template.header("X-Internal-Key", apiKey);
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                template.header("X-Correlation-Id", correlationId);
            }
        };
    }

    @Bean
    ErrorDecoder accountServiceErrorDecoder(ObjectMapper objectMapper) {
        return new AccountServiceErrorDecoder(objectMapper);
    }

    /**
     * Turns account-service's RFC 7807 business errors (400/404/409/422) into TransferRejectedException.
     * Everything else (5xx, 401/403 misconfiguration, 429) stays a technical failure that the
     * circuit breaker counts.
     */
    static class AccountServiceErrorDecoder implements ErrorDecoder {

        private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        private final ObjectMapper objectMapper;

        AccountServiceErrorDecoder(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public Exception decode(String methodKey, Response response) {
            int status = response.status();
            boolean business = status == 400 || status == 404 || status == 409 || status == 422;
            if (!business) {
                return defaultDecoder.decode(methodKey, response);
            }
            String code = "REJECTED";
            String detail = "Rejected by account-service";
            if (response.body() != null) {
                try (InputStream body = response.body().asInputStream()) {
                    JsonNode json = objectMapper.readTree(body);
                    code = json.path("code").asText(code);
                    detail = json.path("detail").asText(detail);
                } catch (IOException ignored) {
                    // keep defaults
                }
            }
            return new TransferRejectedException(HttpStatus.valueOf(status), code, detail);
        }
    }
}
