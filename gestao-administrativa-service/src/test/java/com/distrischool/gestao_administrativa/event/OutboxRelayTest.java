package com.distrischool.gestao_administrativa.event;

import com.distrischool.gestao_administrativa.event.ScheduleUpdatedEvent.Acao;
import com.distrischool.gestao_administrativa.event.ScheduleUpdatedEvent.DisciplinaOfertada;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Relay da outbox sem Spring: repositório e Kafka simulados. */
class OutboxRelayTest {

    private static final Instant AGORA = Instant.parse("2026-11-09T12:00:00Z");

    private OutboxRepository repository;
    private KafkaOperations<String, byte[]> kafka;
    private OutboxRelay relay;
    /** Registros enviados ao Kafka, na ordem de envio. */
    private final List<ProducerRecord<String, byte[]>> enviados = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        repository = mock(OutboxRepository.class);
        kafka = mock(KafkaOperations.class);
        relay = novoRelay(Duration.ofSeconds(5));
    }

    private OutboxRelay novoRelay(Duration prazoLote) {
        return new OutboxRelay(repository, kafka, new TransactionTemplate(mock(PlatformTransactionManager.class)),
                new OutboxRelay.Configuracao(50, prazoLote, Duration.ofDays(7)), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static OutboxEvento evento(long id, String topico, String chave) {
        return new OutboxEvento(id, topico, chave, "{\"id\":" + id + "}", 0);
    }

    private void pendentes(OutboxEvento... eventos) {
        when(repository.bloquearPendentes(50)).thenReturn(List.of(eventos));
    }

    /** O Kafka responde a cada envio com o resultado indicado para o id do evento. */
    private void kafkaResponde(Map<Long, CompletableFuture<SendResult<String, byte[]>>> respostas) {
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(inv -> {
            ProducerRecord<String, byte[]> registro = inv.getArgument(0);
            enviados.add(registro);
            String payload = new String(registro.value(), StandardCharsets.UTF_8);
            long id = Long.parseLong(payload.replaceAll("\\D", ""));
            return respostas.getOrDefault(id, sucesso());
        });
    }

    private static CompletableFuture<SendResult<String, byte[]>> sucesso() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<SendResult<String, byte[]>> falha(Throwable causa) {
        return CompletableFuture.failedFuture(new KafkaProducerException(null, "Failed to send", causa));
    }

    @Test
    void publicaOsPendentesEMarcaComoPublicados() {
        pendentes(evento(1, "schedule.updated", "12"), evento(2, "staff.created", "5"));
        kafkaResponde(Map.of());
        when(repository.contarPendentes()).thenReturn(0L);

        relay.publicarPendentes();

        assertThat(enviados).extracting(ProducerRecord::topic).containsExactly("schedule.updated", "staff.created");
        assertThat(enviados).extracting(ProducerRecord::key).containsExactly("12", "5");
        verify(repository).marcarPublicado(1L);
        verify(repository).marcarPublicado(2L);
        verify(repository, never()).registrarFalha(anyLong(), anyString());
    }

    @Test
    void falhaDoKafkaIncrementaTentativasEGuardaOErroSemMarcarPublicado() {
        pendentes(evento(1, "schedule.updated", "12"));
        kafkaResponde(Map.of(1L, falha(new org.apache.kafka.common.errors.RecordTooLargeException("grande demais"))));

        relay.publicarPendentes();

        verify(repository).registrarFalha(eq(1L), contains("RecordTooLargeException: grande demais"));
        verify(repository, never()).marcarPublicado(anyLong());
    }

    @Test
    void falhaDeUmEventoNaoImpedeOsDeOutrasChaves() {
        pendentes(evento(1, "schedule.updated", "12"), evento(2, "schedule.updated", "13"),
                evento(3, "teacher.assigned", "14"));
        CompletableFuture<SendResult<String, byte[]>> recusado = falha(new IllegalStateException("recusado"));
        kafkaResponde(Map.of(2L, recusado));

        relay.publicarPendentes();

        verify(repository).marcarPublicado(1L);
        verify(repository).registrarFalha(eq(2L), contains("recusado"));
        verify(repository).marcarPublicado(3L);
        verify(repository, never()).marcarPublicado(2L);
    }

    @Test
    void eventosDaMesmaChaveSaemEmOrdemEOsSeguintesAUmaFalhaFicamParaODepois() {
        pendentes(evento(1, "schedule.updated", "12"), evento(2, "schedule.updated", "12"),
                evento(3, "schedule.updated", "12"), evento(4, "schedule.updated", "13"),
                evento(5, "schedule.updated", "13"));
        CompletableFuture<SendResult<String, byte[]>> recusado = falha(new IllegalStateException("recusado"));
        kafkaResponde(Map.of(2L, recusado));

        relay.publicarPendentes();

        // Turma 12: o 1 sai, o 2 falha e o 3 nem é enviado; a turma 13 segue normalmente
        assertThat(enviados).extracting(r -> new String(r.value(), StandardCharsets.UTF_8))
                .containsExactly("{\"id\":1}", "{\"id\":4}", "{\"id\":2}", "{\"id\":5}");
        verify(repository).marcarPublicado(1L);
        verify(repository).registrarFalha(eq(2L), anyString());
        verify(repository, never()).marcarPublicado(2L);
        verify(repository, never()).registrarFalha(eq(3L), anyString());
        verify(repository).marcarPublicado(4L);
        verify(repository).marcarPublicado(5L);
    }

    @Test
    void kafkaInacessivelEncerraOCicloSemContarTentativaNoRestoDoLote() {
        pendentes(evento(1, "schedule.updated", "12"), evento(2, "schedule.updated", "13"),
                evento(3, "schedule.updated", "14"));
        kafkaResponde(Map.of(1L, falha(new org.apache.kafka.common.errors.TimeoutException(
                "Topic schedule.updated not present in metadata after 5000 ms."))));

        relay.publicarPendentes();

        assertThat(enviados).hasSize(1);
        verify(repository).registrarFalha(eq(1L), contains("not present in metadata"));
        verify(repository, never()).registrarFalha(eq(2L), anyString());
        verify(repository, never()).registrarFalha(eq(3L), anyString());
        verify(repository, never()).marcarPublicado(anyLong());
    }

    @Test
    void envioSemConfirmacaoDentroDoPrazoContaComoFalha() {
        relay = novoRelay(Duration.ofMillis(50));
        pendentes(evento(1, "schedule.updated", "12"));
        kafkaResponde(Map.of(1L, new CompletableFuture<>()));

        relay.publicarPendentes();

        verify(repository).registrarFalha(eq(1L), contains("Sem confirmação do Kafka"));
        verify(repository, never()).marcarPublicado(anyLong());
    }

    @Test
    void excecaoNoProprioSendTambemERegistrada() {
        pendentes(evento(1, "schedule.updated", "12"), evento(2, "schedule.updated", "13"));
        when(kafka.send(any(ProducerRecord.class)))
                .thenThrow(new IllegalStateException("produtor fechado"))
                .thenReturn(sucesso());

        relay.publicarPendentes();

        verify(repository).registrarFalha(eq(1L), contains("produtor fechado"));
        verify(repository).marcarPublicado(2L);
    }

    @Test
    void loteVazioNaoEnviaNadaEAtualizaAContagemDePendentes() {
        pendentes();
        when(repository.contarPendentes()).thenReturn(3L);

        relay.publicarPendentes();

        verify(repository, never()).marcarPublicado(anyLong());
        assertThat(relay.pendentes()).isEqualTo(3L);
    }

    @Test
    void limpezaApagaOsPublicadosForaDaRetencao() {
        relay.limparPublicados();

        verify(repository).apagarPublicadosAntesDe(Instant.parse("2026-11-02T12:00:00Z"));
    }

    /**
     * Ponta a ponta sem banco: o payload que o KafkaEventPublisher grava na outbox chega ao Kafka com
     * os mesmos bytes que o JacksonJsonSerializer do produtor geraria para o evento (JSON de objeto,
     * não uma string entre aspas) e sem cabeçalhos.
     */
    @Test
    void mensagemNoKafkaEByteAByteIgualAoSerializadorDoProdutor() {
        ProducerFactory<?, ?> producerFactory = mock(ProducerFactory.class);
        when(producerFactory.getConfigurationProperties())
                .thenReturn(Map.of("spring.json.add.type.headers", "false"));
        KafkaEventPublisher publisher = new KafkaEventPublisher(repository, producerFactory);
        ScheduleUpdatedEvent evento = new ScheduleUpdatedEvent(
                12L, "9A-2026", "9º Ano A", 2026, "MANHA", null,
                List.of(new DisciplinaOfertada(3L, "MAT", "Matemática", 7L)),
                Acao.ATUALIZADA, Instant.parse("2026-10-24T11:00:00Z"));

        publisher.on(evento);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(repository).inserir(eq("schedule.updated"), eq("12"), payload.capture());

        pendentes(new OutboxEvento(1, "schedule.updated", "12", payload.getValue(), 0));
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(inv -> {
            enviados.add(inv.getArgument(0));
            return sucesso();
        });
        relay.publicarPendentes();

        byte[] esperado;
        try (JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>()) {
            serializer.configure(Map.of("spring.json.add.type.headers", false), false);
            esperado = serializer.serialize("schedule.updated", evento);
        }
        ProducerRecord<String, byte[]> registro = enviados.getFirst();
        assertThat(registro.topic()).isEqualTo("schedule.updated");
        assertThat(registro.key()).isEqualTo("12");
        assertThat(registro.value()).isEqualTo(esperado);
        assertThat(new String(registro.value(), StandardCharsets.UTF_8)).startsWith("{\"turmaId\":12,");
        assertThat(registro.headers().toArray()).isEmpty();
    }
}
