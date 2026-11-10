package com.distrischool.gestao_escolar.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Gravação dos eventos na outbox, com o tópico, a chave e o JSON que vão para o Kafka. */
class KafkaEventPublisherTest {

    private static final Instant OCORRIDO = Instant.parse("2026-10-24T11:00:00Z");

    private OutboxRepository outbox;
    private KafkaEventPublisher publisher;

    @BeforeEach
    void preparar() {
        outbox = mock(OutboxRepository.class);
        ProducerFactory<?, ?> producerFactory = mock(ProducerFactory.class);
        when(producerFactory.getConfigurationProperties())
                .thenReturn(Map.of("spring.json.add.type.headers", "false"));
        publisher = new KafkaEventPublisher(outbox, producerFactory);
    }

    @Test
    void studentCreatedUsaAlunoIdComoChave() {
        publisher.on(new StudentCreatedEvent(1L, "2026000001", "Maria Silva", "maria@example.com", OCORRIDO));

        JsonNode json = payloadGravado(KafkaTopics.STUDENT_CREATED, "1");
        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "alunoId", "matricula", "nome", "email", "occurredAt");
    }

    @Test
    void enrollmentCreatedEUpdatedUsamAlunoIdComoChave() {
        publisher.on(new EnrollmentCreatedEvent(4L, 1L, 12L, 2026, OCORRIDO));
        publisher.on(new EnrollmentUpdatedEvent(4L, 1L, 12L, "ATIVA", "TRANCADA", null, OCORRIDO));

        assertThat(payloadGravado(KafkaTopics.ENROLLMENT_CREATED, "1").get("matriculaId").asLong()).isEqualTo(4L);
        assertThat(payloadGravado(KafkaTopics.ENROLLMENT_UPDATED, "1").get("statusNovo").asString()).isEqualTo("TRANCADA");
    }

    @Test
    void attendanceRecordedUsaTurmaIdComoChave() {
        publisher.on(new AttendanceRecordedEvent(500L, 12L, 3L, LocalDate.of(2026, 10, 20),
                List.of(new AttendanceRecordedEvent.Ausencia(2L, "FALTA")), OCORRIDO));

        JsonNode json = payloadGravado(KafkaTopics.ATTENDANCE_RECORDED, "12");
        assertThat(json.isObject()).isTrue();
        assertThat(json.get("data").asString()).isEqualTo("2026-10-20");
    }

    /** Sem transação ativa o evento não pode ser descartado em silêncio: a gravação exige uma. */
    @Test
    void todosOsListenersExigemTransacaoAtiva() {
        Method[] listeners = Arrays.stream(KafkaEventPublisher.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(EventListener.class))
                .toArray(Method[]::new);

        assertThat(listeners).hasSize(5);
        assertThat(listeners).allSatisfy(m -> assertThat(m.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY));
    }

    private JsonNode payloadGravado(String topico, String chave) {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).inserir(eq(topico), eq(chave), payload.capture());
        return JsonMapper.builder().build().readTree(payload.getValue());
    }
}
