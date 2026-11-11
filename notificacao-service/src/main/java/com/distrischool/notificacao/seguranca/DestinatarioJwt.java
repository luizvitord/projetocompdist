package com.distrischool.notificacao.seguranca;

import com.distrischool.notificacao.notificacao.Destinatario;
import com.distrischool.notificacao.notificacao.Perfil;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Lê o destinatário das claims {@code perfil} e {@code referenciaId} do token do usuarios-service. */
public final class DestinatarioJwt {

    static final String CLAIM_PERFIL = "perfil";
    static final String CLAIM_REFERENCIA_ID = "referenciaId";

    private DestinatarioJwt() {
    }

    /** O token já passou por {@link #perfilValido()}, então a claim {@code perfil} é um perfil conhecido. */
    public static Destinatario de(Jwt jwt) {
        Perfil perfil = Perfil.valueOf(jwt.getClaimAsString(CLAIM_PERFIL));
        // Número JSON: chega como Long ou Integer, conforme o tamanho
        Long referenciaId = jwt.getClaim(CLAIM_REFERENCIA_ID) instanceof Number numero ? numero.longValue() : null;
        return Destinatario.de(perfil, referenciaId);
    }

    /** Recusa (401) tokens sem a claim {@code perfil} ou com um perfil desconhecido. */
    public static OAuth2TokenValidator<Jwt> perfilValido() {
        return jwt -> perfilConhecido(jwt.getClaimAsString(CLAIM_PERFIL))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
                        "Token sem um perfil válido", null));
    }

    private static boolean perfilConhecido(String perfil) {
        if (perfil == null) {
            return false;
        }
        try {
            Perfil.valueOf(perfil);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
