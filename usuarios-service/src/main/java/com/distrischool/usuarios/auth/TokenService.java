package com.distrischool.usuarios.auth;

import com.distrischool.usuarios.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Gera o JWT entregue no login. O api-gateway lê a claim {@code perfil} para decidir o que o
 * usuário pode fazer.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    static final String EMISSOR = "distrischool-usuarios";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public TokenGerado gerar(Usuario usuario) {
        // O JWT guarda datas em segundos; truncar mantém expiraEm igual à claim exp
        Instant agora = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEm = agora.plus(properties.validade());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(EMISSOR)
                .subject(String.valueOf(usuario.getId()))
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .claim("nome", usuario.getNome())
                .claim("email", usuario.getEmail())
                .claim("perfil", usuario.getPerfil().name());
        if (usuario.getReferenciaId() != null) {
            claims.claim("referenciaId", usuario.getReferenciaId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new TokenGerado(token, expiraEm);
    }

    public record TokenGerado(String token, Instant expiraEm) {
    }
}
