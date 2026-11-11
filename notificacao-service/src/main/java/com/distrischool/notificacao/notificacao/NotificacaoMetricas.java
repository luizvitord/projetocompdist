package com.distrischool.notificacao.notificacao;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Métricas próprias do serviço. No Prometheus:
 * <ul>
 *   <li>{@code notificacoes_criadas_total{tipo}}: notificações gravadas (reentregas não contam);</li>
 *   <li>{@code notificacoes_enviadas_total{canal}}: entregas em tempo real ({@code websocket});</li>
 *   <li>{@code websocket_sessoes_ativas}: sessões WebSocket abertas nesta réplica.</li>
 * </ul>
 * Os contadores são registrados na subida, para aparecerem com zero antes do primeiro evento.
 */
@Component
public class NotificacaoMetricas {

    public static final String CANAL_WEBSOCKET = "websocket";

    private final Map<TipoNotificacao, Counter> criadas = new EnumMap<>(TipoNotificacao.class);
    private final Counter enviadasWebsocket;
    private final AtomicInteger sessoesWebsocket = new AtomicInteger();

    public NotificacaoMetricas(MeterRegistry registry) {
        for (TipoNotificacao tipo : TipoNotificacao.values()) {
            criadas.put(tipo, Counter.builder("notificacoes.criadas")
                    .description("Notificações gravadas, por tipo")
                    .tag("tipo", tipo.name())
                    .register(registry));
        }
        enviadasWebsocket = Counter.builder("notificacoes.enviadas")
                .description("Notificações entregues em tempo real, por canal")
                .tag("canal", CANAL_WEBSOCKET)
                .register(registry);
        Gauge.builder("websocket.sessoes.ativas", sessoesWebsocket, AtomicInteger::get)
                .description("Sessões WebSocket (STOMP) abertas nesta réplica")
                .register(registry);
    }

    public void criada(TipoNotificacao tipo) {
        criadas.get(tipo).increment();
    }

    public void enviadaPorWebsocket() {
        enviadasWebsocket.increment();
    }

    public void sessaoWebsocketAberta() {
        sessoesWebsocket.incrementAndGet();
    }

    public void sessaoWebsocketFechada() {
        sessoesWebsocket.updateAndGet(atual -> Math.max(0, atual - 1));
    }
}
