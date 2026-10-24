package com.distrischool.gestao_escolar.event;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o formato dos eventos publicados por este serviço. Usa o mesmo serializador configurado
 * em application.properties, sem cabeçalhos de tipo Java.
 */
class EventContractTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private JsonNode serializar(String topico, Object evento) {
        try (JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>()) {
            serializer.configure(Map.of("spring.json.add.type.headers", false), false);
            byte[] bytes = serializer.serialize(topico, evento);
            return mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    @Test
    void studentCreated() {
        JsonNode json = serializar(KafkaTopics.STUDENT_CREATED, new StudentCreatedEvent(
                1L, "2026000001", "Maria Silva", "maria@example.com", Instant.parse("2026-10-24T11:00:00Z")));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "alunoId", "matricula", "nome", "email", "occurredAt");
        assertThat(json.get("alunoId").asLong()).isEqualTo(1L);
        assertThat(json.get("matricula").asString()).isEqualTo("2026000001");
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-10-24T11:00:00Z");
    }

    @Test
    void enrollmentCreated() {
        JsonNode json = serializar(KafkaTopics.ENROLLMENT_CREATED, new EnrollmentCreatedEvent(
                4L, 1L, 12L, 2026, Instant.parse("2026-10-24T11:00:00Z")));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "matriculaId", "alunoId", "turmaId", "anoLetivo", "occurredAt");
        assertThat(json.get("matriculaId").asLong()).isEqualTo(4L);
        assertThat(json.get("anoLetivo").asInt()).isEqualTo(2026);
    }

    @Test
    void enrollmentUpdatedEmTransferencia() {
        JsonNode json = serializar(KafkaTopics.ENROLLMENT_UPDATED, new EnrollmentUpdatedEvent(
                4L, 1L, 12L, "ATIVA", "TRANSFERIDA", 15L, Instant.parse("2026-10-24T11:00:00Z")));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "matriculaId", "alunoId", "turmaId", "statusAnterior", "statusNovo", "turmaDestinoId", "occurredAt");
        assertThat(json.get("statusAnterior").asString()).isEqualTo("ATIVA");
        assertThat(json.get("statusNovo").asString()).isEqualTo("TRANSFERIDA");
        assertThat(json.get("turmaDestinoId").asLong()).isEqualTo(15L);
    }

    @Test
    void enrollmentUpdatedForaDeTransferenciaTemTurmaDestinoNula() {
        JsonNode json = serializar(KafkaTopics.ENROLLMENT_UPDATED, new EnrollmentUpdatedEvent(
                4L, 1L, 12L, "ATIVA", "TRANCADA", null, Instant.parse("2026-10-24T11:00:00Z")));

        assertThat(json.has("turmaDestinoId")).isTrue();
        assertThat(json.get("turmaDestinoId").isNull()).isTrue();
    }
}
