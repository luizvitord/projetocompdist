package com.distrischool.notificacao.seguranca;

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

/** Assina tokens como o usuarios-service, com o segredo padrão de desenvolvimento do serviço. */
public final class TestTokens {

    public static final String SEGREDO = "distrischool-segredo-de-desenvolvimento-local";

    private TestTokens() {
    }

    public static String bearer(String perfil, Long referenciaId) {
        return "Bearer " + token(perfil, referenciaId);
    }

    public static String token(String perfil, Long referenciaId) {
        return token(perfil, referenciaId, SEGREDO, Instant.now().plus(Duration.ofHours(1)));
    }

    public static String token(String perfil, Long referenciaId, String segredo, Instant expiraEm) {
        var chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("distrischool-usuarios")
                .subject("1")
                .issuedAt(expiraEm.minus(Duration.ofHours(8)))
                .expiresAt(expiraEm)
                .claim("nome", "Usuário de Teste")
                .claim("email", "teste@distrischool.com")
                .claim("sessao", 1L);
        if (perfil != null) {
            claims.claim("perfil", perfil);
        }
        if (referenciaId != null) {
            claims.claim("referenciaId", referenciaId);
        }
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }
}
