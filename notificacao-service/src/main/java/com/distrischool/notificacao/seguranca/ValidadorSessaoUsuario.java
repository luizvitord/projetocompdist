package com.distrischool.notificacao.seguranca;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Consulta a fonte de verdade antes de aceitar uma nova conexão WebSocket. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidadorSessaoUsuario {

    private final RestClient usuariosRestClient;

    public boolean valida(Jwt jwt) {
        try {
            long id = Long.parseLong(jwt.getSubject());
            Number versaoNoToken = jwt.getClaim("sessao");
            if (versaoNoToken == null) {
                return false;
            }
            EstadoSessao estado = usuariosRestClient.get()
                    .uri("/interno/sessoes/{id}", id)
                    .retrieve()
                    .body(EstadoSessao.class);
            return estado != null && estado.ativo() && estado.versaoSessao() == versaoNoToken.longValue();
        } catch (NumberFormatException | RestClientException ex) {
            log.warn("Não foi possível validar o estado da sessão WebSocket", ex);
            return false;
        }
    }

    private record EstadoSessao(boolean ativo, long versaoSessao) {
    }
}
