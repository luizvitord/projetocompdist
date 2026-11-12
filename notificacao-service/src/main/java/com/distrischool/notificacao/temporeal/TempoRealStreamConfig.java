package com.distrischool.notificacao.temporeal;

import com.distrischool.notificacao.evento.LeitorEventos;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * Consumidor do tópico interno {@code notificacao.criada}, pelo binding
 * {@code entregarNotificacao-in-0}. O binding não tem {@code group}: o Spring Cloud Stream cria
 * então um grupo anônimo e exclusivo para cada instância, e todas recebem todos os avisos
 * (fan-out), ao contrário do grupo compartilhado dos eventos de domínio.
 */
@Configuration
public class TempoRealStreamConfig {

    static final String TOPICO = "notificacao.criada";

    @Bean
    Consumer<Message<byte[]>> entregarNotificacao(LeitorEventos leitor, EntregadorTempoReal entregador) {
        return mensagem -> entregador.entregar(
                leitor.ler(TOPICO, mensagem.getPayload(), NotificacaoCriadaMensagem.class));
    }
}
