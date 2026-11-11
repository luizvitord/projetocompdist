package com.distrischool.notificacao.evento;

import com.distrischool.notificacao.common.MensagemInvalidaException;
import com.distrischool.notificacao.nomes.CacheNomesService;
import com.distrischool.notificacao.nomes.ScheduleUpdatedMessage;
import com.distrischool.notificacao.notificacao.Destinatario;
import com.distrischool.notificacao.notificacao.InsercaoEmMemoria;
import com.distrischool.notificacao.notificacao.NotificacaoCriada;
import com.distrischool.notificacao.notificacao.NotificacaoMetricas;
import com.distrischool.notificacao.notificacao.NotificacaoRepository;
import com.distrischool.notificacao.notificacao.NotificacaoService;
import com.distrischool.notificacao.notificacao.Perfil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Do registro do Kafka até a gravação, sem Spring: mapeador e service reais, com a inserção em
 * memória que segue a regra do índice único.
 */
class ProcessadorEventosTest {

    private static final String GRADE_POSTED = """
            {"avaliacaoId":9,"turmaId":12,"disciplinaId":3,"bimestre":1,\
            "notas":[{"alunoId":1,"valor":8.50},{"alunoId":2,"valor":6.00}],"occurredAt":"2026-10-24T11:00:00Z"}""";

    private InsercaoEmMemoria insercao;
    private CacheNomesService cacheNomes;
    private ProcessadorEventos processador;

    @BeforeEach
    void preparar() {
        insercao = new InsercaoEmMemoria();
        cacheNomes = mock(CacheNomesService.class);
        when(cacheNomes.nomeTurma(anyLong())).thenReturn(Optional.empty());
        when(cacheNomes.nomeDisciplina(3L)).thenReturn(Optional.of("Matemática"));
        NotificacaoService notificacaoService = new NotificacaoService(mock(NotificacaoRepository.class), insercao,
                new NotificacaoMetricas(new SimpleMeterRegistry()), mock(ApplicationEventPublisher.class),
                mock(PlatformTransactionManager.class), Clock.systemUTC());
        LeitorEventos leitor = new LeitorEventos();
        processador = new ProcessadorEventos(new MapeadorEventos(leitor, cacheNomes), leitor, cacheNomes,
                notificacaoService);
    }

    /** Registro como o binder Kafka entrega: payload e chave em bytes. */
    private static Message<byte[]> registro(String chave, String payload, long offset) {
        return MessageBuilder.withPayload(payload.getBytes(StandardCharsets.UTF_8))
                .setHeader(KafkaHeaders.RECEIVED_KEY, chave.getBytes(StandardCharsets.UTF_8))
                .setHeader(KafkaHeaders.OFFSET, offset)
                .build();
    }

    @Test
    void eventoGeraUmaNotificacaoPorDestinatario() {
        processador.processar(KafkaTopics.GRADE_POSTED, registro("12", GRADE_POSTED, 40L));

        assertThat(insercao.gravadas()).extracting(NotificacaoCriada::destinatario).containsExactly(
                Destinatario.de(Perfil.ALUNO, 1L), Destinatario.de(Perfil.RESPONSAVEL, 1L),
                Destinatario.de(Perfil.ALUNO, 2L), Destinatario.de(Perfil.RESPONSAVEL, 2L));
        assertThat(insercao.gravadas().get(0).mensagem()).isEqualTo("Nova nota em Matemática (1º bimestre): 8,5");
    }

    @Test
    void oMesmoEventoEntregueDuasVezesGeraUmaNotificacaoPorDestinatario() {
        processador.processar(KafkaTopics.GRADE_POSTED, registro("12", GRADE_POSTED, 40L));
        // Reenvio da outbox do produtor: mesmo conteúdo, outro offset
        processador.processar(KafkaTopics.GRADE_POSTED, registro("12", GRADE_POSTED, 41L));

        assertThat(insercao.gravadas()).hasSize(4);
        assertThat(insercao.gravadas()).extracting(NotificacaoCriada::destinatario).doesNotHaveDuplicates();
    }

    @Test
    void outroEventoParaOsMesmosAlunosGeraNovasNotificacoes() {
        processador.processar(KafkaTopics.GRADE_POSTED, registro("12", GRADE_POSTED, 40L));
        // Correção de nota: mesmo aluno e avaliação, outro occurredAt
        processador.processar(KafkaTopics.GRADE_POSTED, registro("12",
                GRADE_POSTED.replace("2026-10-24T11:00:00Z", "2026-10-25T09:30:00Z"), 41L));

        assertThat(insercao.gravadas()).hasSize(8);
    }

    @Test
    void scheduleUpdatedSoAtualizaOCacheDeNomes() {
        processador.processar(KafkaTopics.SCHEDULE_UPDATED, registro("12", """
                {"turmaId":12,"codigo":"9A-2026","nome":"9º Ano A","anoLetivo":2026,"turno":"MANHA","capacidade":35,
                 "disciplinas":[{"disciplinaId":3,"codigo":"MAT","nome":"Matemática","professorId":7}],
                 "acao":"ATUALIZADA","occurredAt":"2026-10-24T11:00:00Z"}
                """, 5L));

        ArgumentCaptor<ScheduleUpdatedMessage> mensagem = ArgumentCaptor.forClass(ScheduleUpdatedMessage.class);
        verify(cacheNomes).aplicar(mensagem.capture());
        assertThat(mensagem.getValue()).isEqualTo(new ScheduleUpdatedMessage(12L, "9A-2026", "9º Ano A",
                java.util.List.of(new ScheduleUpdatedMessage.DisciplinaOfertada(3L, "Matemática")),
                Instant.parse("2026-10-24T11:00:00Z")));
        assertThat(insercao.gravadas()).isEmpty();
    }

    @Test
    void payloadInvalidoLancaMensagemInvalidaENaoGravaNada() {
        assertThatThrownBy(() -> processador.processar(KafkaTopics.GRADE_POSTED, registro("12", "{ quebrado", 40L)))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> processador.processar(KafkaTopics.SCHEDULE_UPDATED, registro("12", "[1, 2", 5L)))
                .isInstanceOf(MensagemInvalidaException.class);

        assertThat(insercao.gravadas()).isEmpty();
        verify(cacheNomes, never()).aplicar(any());
    }

    @Test
    void registroSemChaveTambemEProcessado() {
        Message<byte[]> semChave = MessageBuilder.withPayload(GRADE_POSTED.getBytes(StandardCharsets.UTF_8)).build();

        processador.processar(KafkaTopics.GRADE_POSTED, semChave);

        assertThat(insercao.gravadas()).hasSize(4);
    }
}
