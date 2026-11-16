package com.distrischool.api_gateway.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** Signs tokens the same way usuarios-service does, using the gateway's default dev secret. */
final class TestTokens {

    static final String SECRET = "distrischool-segredo-de-desenvolvimento-local";

    private TestTokens() {
    }

    static String bearer(String perfil) {
        return "Bearer " + token(perfil, SECRET, Instant.now().plus(Duration.ofHours(1)));
    }

    /** Token whose {@code referenciaId} claim is the given value (omitted when null). */
    static String bearer(String perfil, Object referenciaId) {
        return "Bearer " + token(perfil, referenciaId, SECRET, Instant.now().plus(Duration.ofHours(1)));
    }

    static String bearerComSessao(String perfil, long usuarioId, long versaoSessao) {
        return "Bearer " + token(perfil, null, SECRET, Instant.now().plus(Duration.ofHours(1)), usuarioId, versaoSessao);
    }

    static String token(String perfil, String secret, Instant expiresAt) {
        return token(perfil, null, secret, expiresAt);
    }

    static String token(String perfil, Object referenciaId, String secret, Instant expiresAt) {
        return token(perfil, referenciaId, secret, expiresAt, 1L, 1L);
    }

    private static String token(String perfil, Object referenciaId, String secret, Instant expiresAt,
                                long usuarioId, long versaoSessao) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .subject(String.valueOf(usuarioId))
                .issuedAt(expiresAt.minus(Duration.ofHours(8)))
                .expiresAt(expiresAt)
                .claim("perfil", perfil)
                .claim("sessao", versaoSessao);
        if (referenciaId != null) {
            builder.claim("referenciaId", referenciaId);
        }
        JwtClaimsSet claims = builder.build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
