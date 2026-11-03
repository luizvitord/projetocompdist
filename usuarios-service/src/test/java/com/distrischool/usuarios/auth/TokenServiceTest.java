package com.distrischool.usuarios.auth;

import com.distrischool.usuarios.usuario.Perfil;
import com.distrischool.usuarios.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Decodifica o token do mesmo jeito que o api-gateway faz, com o segredo compartilhado. */
class TokenServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-pelo-menos-32-bytes";
    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    private final JwtProperties properties = new JwtProperties(SEGREDO, Duration.ofHours(8));

    private TokenService tokenService(Instant agora) {
        return new TokenService(new JwtConfig().jwtEncoder(properties), properties,
                Clock.fixed(agora, ZoneOffset.UTC));
    }

    private static JwtDecoder decoder(String segredo) {
        var chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static Usuario professor() {
        Usuario usuario = new Usuario();
        usuario.setId(4L);
        usuario.setNome("Maria Lima");
        usuario.setEmail("maria@distrischool.com");
        usuario.setPerfil(Perfil.PROFESSOR);
        usuario.setReferenciaId(3L);
        return usuario;
    }

    @Test
    void tokenTrazIdentidadePerfilEExpiracao() {
        // Emitido agora, para o decoder não recusar por expiração
        TokenService.TokenGerado gerado = tokenService(Instant.now()).gerar(professor());

        Jwt jwt = decoder(SEGREDO).decode(gerado.token());

        assertThat(jwt.getSubject()).isEqualTo("4");
        assertThat(jwt.getClaimAsString("perfil")).isEqualTo("PROFESSOR");
        assertThat(jwt.getClaimAsString("nome")).isEqualTo("Maria Lima");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("maria@distrischool.com");
        assertThat(jwt.<Number>getClaim("referenciaId").longValue()).isEqualTo(3L);
        assertThat(jwt.getExpiresAt()).isEqualTo(gerado.expiraEm());
    }

    @Test
    void expiraDepoisDaValidadeConfigurada() {
        TokenService.TokenGerado gerado = tokenService(AGORA).gerar(professor());

        assertThat(gerado.expiraEm()).isEqualTo(AGORA.plus(Duration.ofHours(8)));
        assertThatThrownBy(() -> decoder(SEGREDO).decode(gerado.token()))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tokenAssinadoComOutroSegredoERecusado() {
        TokenService.TokenGerado gerado = tokenService(Instant.now()).gerar(professor());

        assertThatThrownBy(() -> decoder("outro-segredo-qualquer-com-mais-de-32-bytes").decode(gerado.token()))
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void recusaSegredoCurto() {
        assertThatThrownBy(() -> new JwtProperties("curto", Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
