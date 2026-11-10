package com.distrischool.gestao_administrativa.event;

import com.distrischool.gestao_administrativa.tecnico.Cargo;
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
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Gravação dos eventos na outbox, com o tópico, a chave e o JSON que vão para o Kafka. */
class KafkaEventPublisherTest {

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
    void gravaTeacherAssignedComChaveTurmaId() {
        publisher.on(new TeacherAssignedEvent(7L, "Ana", "ana@example.com", 12L, "9A-2026", "9º Ano A",
                Instant.parse("2026-10-24T11:00:00Z")));

        JsonNode json = payloadGravado(KafkaTopics.TEACHER_ASSIGNED, "12");
        assertThat(json.get("professorId").asLong()).isEqualTo(7L);
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-10-24T11:00:00Z");
    }

    @Test
    void gravaStaffCreatedComChaveTecnicoIdComoObjetoJson() {
        publisher.on(new StaffCreatedEvent(5L, "Carlos", "carlos@example.com", Cargo.values()[0], "Secretaria",
                Instant.parse("2026-10-24T11:00:00Z")));

        JsonNode json = payloadGravado(KafkaTopics.STAFF_CREATED, "5");
        assertThat(json.isObject()).isTrue();
        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "tecnicoId", "nome", "email", "cargo", "setor", "occurredAt");
    }

    /** Sem transação ativa o evento não pode ser descartado em silêncio: a gravação exige uma. */
    @Test
    void todosOsListenersExigemTransacaoAtiva() {
        Method[] listeners = Arrays.stream(KafkaEventPublisher.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(EventListener.class))
                .toArray(Method[]::new);

        assertThat(listeners).hasSize(3);
        assertThat(listeners).allSatisfy(m -> assertThat(m.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY));
    }

    private JsonNode payloadGravado(String topico, String chave) {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).inserir(eq(topico), eq(chave), payload.capture());
        return JsonMapper.builder().build().readTree(payload.getValue());
    }
}
