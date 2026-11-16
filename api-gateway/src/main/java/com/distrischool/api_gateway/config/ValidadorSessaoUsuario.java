package com.distrischool.api_gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Confere no usuários-service se um JWT ainda representa uma sessão ativa. */
@Component
public class ValidadorSessaoUsuario {

    private static final Logger log = LoggerFactory.getLogger(ValidadorSessaoUsuario.class);

    private final RestClient usuariosRestClient;

    public ValidadorSessaoUsuario(RestClient usuariosRestClient) {
        this.usuariosRestClient = usuariosRestClient;
    }

    public Resultado validar(Jwt jwt) {
        try {
            long id = Long.parseLong(jwt.getSubject());
            Number versaoNoToken = jwt.getClaim("sessao");
            if (versaoNoToken == null) {
                return Resultado.REVOGADA;
            }
            EstadoSessao estado = usuariosRestClient.get()
                    .uri("/interno/sessoes/{id}", id)
                    .retrieve()
                    .body(EstadoSessao.class);
            return estado != null && estado.ativo() && estado.versaoSessao() == versaoNoToken.longValue()
                    ? Resultado.VALIDA : Resultado.REVOGADA;
        } catch (NumberFormatException | RestClientException ex) {
            log.warn("Não foi possível validar o estado da sessão", ex);
            return Resultado.INDISPONIVEL;
        }
    }

    public enum Resultado { VALIDA, REVOGADA, INDISPONIVEL }

    private record EstadoSessao(boolean ativo, long versaoSessao) {
    }
}
