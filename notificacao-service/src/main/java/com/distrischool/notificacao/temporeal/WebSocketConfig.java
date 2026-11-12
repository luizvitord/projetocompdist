package com.distrischool.notificacao.temporeal;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Notificações em tempo real: WebSocket com STOMP no endpoint {@code /ws}.
 * <ul>
 *   <li>Sem SockJS: uma conexão WebSocket pura não precisa de sessão fixa (sticky session) no
 *       balanceador, diferente dos transportes de fallback do SockJS.</li>
 *   <li>Broker simples em memória, só com {@code /queue}: cada réplica entrega às sessões
 *       conectadas nela. Quem leva a notificação à réplica certa é o tópico
 *       {@code notificacao.criada} (ver {@link EntregadorTempoReal}).</li>
 *   <li>Heartbeats de 10 s nos dois sentidos: conexões mortas são percebidas e fechadas, e
 *       proxies no caminho não derrubam a conexão por inatividade.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final long HEARTBEAT_MS = 10_000;

    private final StompAutenticacaoInterceptor autenticacao;
    private final TaskScheduler heartbeatScheduler;

    /**
     * {@code messageBrokerTaskScheduler} é o agendador que o próprio suporte a WebSocket do Spring
     * cria; {@code @Lazy} porque ele é declarado pela configuração que chama esta classe.
     */
    public WebSocketConfig(StompAutenticacaoInterceptor autenticacao,
                           @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler heartbeatScheduler) {
        this.autenticacao = autenticacao;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Qualquer origem: o navegador chega aqui por um proxy (nginx do frontend, Vite, Ingress),
        // e a conexão não usa cookies. Quem autentica é o JWT do frame CONNECT, que outro site não
        // tem como obter; não há o que um ataque entre origens possa aproveitar.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(heartbeatScheduler);
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(autenticacao);
    }
}
