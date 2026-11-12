package com.distrischool.notificacao.temporeal;

import com.distrischool.notificacao.notificacao.NotificacaoCriada;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Publica no tópico interno {@code notificacao.criada} cada notificação recém-gravada. O
 * {@link NotificacaoCriada} só é publicado pelo NotificacaoService depois do commit, então a
 * réplica que receber o aviso já encontra a notificação no banco.
 * <p>
 * Uma falha aqui não desfaz nada nem repete o evento de origem: a notificação continua gravada e
 * aparece no próximo carregamento da lista (o frontend busca a contagem a cada conexão).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicadorNotificacaoCriada {

    /** Binding de saída ({@code spring.cloud.stream.bindings.notificacaoCriada-out-0.*}). */
    static final String BINDING = "notificacaoCriada-out-0";

    private final StreamBridge streamBridge;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @EventListener
    public void aoCriar(NotificacaoCriada criada) {
        try {
            byte[] payload = mapper.writeValueAsBytes(NotificacaoCriadaMensagem.de(criada));
            // Chave = destinatário: os avisos de uma mesma pessoa ficam na mesma partição, em ordem
            byte[] chave = criada.destinatario().nome().getBytes(StandardCharsets.UTF_8);
            boolean enviado = streamBridge.send(BINDING, MessageBuilder.withPayload(payload)
                    .setHeader(KafkaHeaders.KEY, chave)
                    .build());
            if (!enviado) {
                log.warn("Aviso em tempo real da notificação {} não foi enviado", criada.id());
            }
        } catch (RuntimeException ex) {
            log.warn("Falha ao publicar o aviso em tempo real da notificação {}: {}", criada.id(), ex.toString());
        }
    }
}
