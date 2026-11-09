package com.distrischool.usuarios.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * @param segredo  chave compartilhada com o api-gateway; o HS256 exige pelo menos 32 bytes
 * @param validade tempo até o token expirar; depois disso é preciso entrar de novo
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String segredo, Duration validade) {

    public JwtProperties {
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.segredo deve ter pelo menos 32 bytes");
        }
    }
}
