package com.distrischool.notificacao.temporeal;

import com.distrischool.notificacao.notificacao.Destinatario;
import com.distrischool.notificacao.notificacao.NotificacaoCriada;
import com.distrischool.notificacao.notificacao.Perfil;
import com.distrischool.notificacao.notificacao.TipoNotificacao;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicadorNotificacaoCriadaTest {

    private final StreamBridge streamBridge = mock(StreamBridge.class);
    private final PublicadorNotificacaoCriada publicador = new PublicadorNotificacaoCriada(streamBridge);

    private static NotificacaoCriada criada(Destinatario destinatario) {
        return new NotificacaoCriada(7L, destinatario, TipoNotificacao.ALUNO_CADASTRADO, "Aluno cadastrado",
                "Aluno cadastrado: Ana Souza (matrícula 2026000001)", "/alunos/1", Instant.parse("2026-11-10T12:00:00Z"));
    }

    @SuppressWarnings("unchecked")
    private Message<byte[]> enviada() {
        ArgumentCaptor<Message<byte[]>> mensagem = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(eq("notificacaoCriada-out-0"), mensagem.capture());
        return mensagem.getValue();
    }

    @Test
    void publicaSoOIdEODestinatarioSemOTextoDaNotificacao() {
        when(streamBridge.send(any(), any())).thenReturn(true);

        publicador.aoCriar(criada(Destinatario.de(Perfil.RESPONSAVEL, 12L)));

        Message<byte[]> mensagem = enviada();
        JsonNode json = JsonMapper.builder().build().readTree(mensagem.getPayload());
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("id", "perfil", "referenciaId");
        assertThat(json.get("id").asLong()).isEqualTo(7L);
        assertThat(json.get("perfil").asString()).isEqualTo("RESPONSAVEL");
        assertThat(json.get("referenciaId").asLong()).isEqualTo(12L);
        assertThat(new String(mensagem.getPayload(), StandardCharsets.UTF_8)).doesNotContain("Ana Souza");
        // Chave = destinatário
        assertThat(new String((byte[]) mensagem.getHeaders().get(KafkaHeaders.KEY), StandardCharsets.UTF_8))
                .isEqualTo("RESPONSAVEL:12");
    }

    @Test
    void avisoDaCoordenacaoNaoTemReferencia() {
        when(streamBridge.send(any(), any())).thenReturn(true);

        publicador.aoCriar(criada(Destinatario.admin()));

        JsonNode json = JsonMapper.builder().build().readTree(enviada().getPayload());
        assertThat(json.get("perfil").asString()).isEqualTo("ADMIN");
        assertThat(json.get("referenciaId").isNull()).isTrue();
    }

    @Test
    void falhaNoEnvioNaoPropaga() {
        // A notificação já está gravada; sem o aviso, ela aparece no próximo carregamento da lista
        when(streamBridge.send(any(), any())).thenThrow(new IllegalStateException("Kafka fora do ar"));
        assertThatCode(() -> publicador.aoCriar(criada(Destinatario.admin()))).doesNotThrowAnyException();

        StreamBridge recusa = mock(StreamBridge.class);
        when(recusa.send(any(), any())).thenReturn(false);
        assertThatCode(() -> new PublicadorNotificacaoCriada(recusa).aoCriar(criada(Destinatario.admin())))
                .doesNotThrowAnyException();
    }
}
