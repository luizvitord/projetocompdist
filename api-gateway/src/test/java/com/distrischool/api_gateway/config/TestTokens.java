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

    static String token(String perfil, String secret, Instant expiresAt) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1")
                .issuedAt(expiresAt.minus(Duration.ofHours(8)))
                .expiresAt(expiresAt)
                .claim("perfil", perfil)
                .build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
