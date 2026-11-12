package com.distrischool.notificacao.temporeal;

import com.distrischool.notificacao.notificacao.NotificacaoMetricas;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alimenta a métrica de sessões WebSocket ativas. O evento de desconexão também é publicado para
 * conexões que nunca chegaram a autenticar (CONNECT recusado), então só as sessões que
 * conectaram de fato são contadas na saída.
 */
@Component
@RequiredArgsConstructor
public class SessoesWebSocket {

    private final NotificacaoMetricas metricas;
    private final Set<String> conectadas = ConcurrentHashMap.newKeySet();

    @EventListener
    public void aoConectar(SessionConnectedEvent evento) {
        String sessao = SimpMessageHeaderAccessor.getSessionId(evento.getMessage().getHeaders());
        if (sessao != null && conectadas.add(sessao)) {
            metricas.sessaoWebsocketAberta();
        }
    }

    @EventListener
    public void aoDesconectar(SessionDisconnectEvent evento) {
        if (conectadas.remove(evento.getSessionId())) {
            metricas.sessaoWebsocketFechada();
        }
    }
}
