package com.distrischool.api_gateway.resiliencia;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Retry das consultas e rate limiting do login. Timeouts do cliente HTTP ficam em
 * {@code spring.http.clients.*} e o circuit breaker em {@code resilience4j.circuitbreaker.*}.
 */
@ConfigurationProperties(prefix = "gateway.resiliencia")
public record ResilienciaProperties(Retry retry, Login login) {

    /**
     * @param novasTentativas tentativas além da primeira, só para GET
     * @param esperaInicial   espera antes da primeira nova tentativa; dobra a cada uma
     */
    public record Retry(int novasTentativas, Duration esperaInicial) {
    }

    /** No máximo {@code tentativas} logins por IP a cada {@code periodo}. */
    public record Login(long tentativas, Duration periodo) {
    }
}
