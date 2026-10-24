package com.distrischool.gestao_escolar.event;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

    @Test
    void attendanceRecorded() {
        JsonNode json = serializar(KafkaTopics.ATTENDANCE_RECORDED, new AttendanceRecordedEvent(
                500L, 12L, 3L, LocalDate.of(2026, 10, 20),
                List.of(new AttendanceRecordedEvent.Ausencia(2L, "FALTA"), new AttendanceRecordedEvent.Ausencia(3L, "ATRASO")),
                Instant.parse("2026-10-24T11:00:00Z")));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "chamadaId", "turmaId", "disciplinaId", "data", "ausencias", "occurredAt");
        assertThat(json.get("data").asString()).isEqualTo("2026-10-20");
        JsonNode ausencia = json.get("ausencias").get(0);
        assertThat(ausencia.propertyNames()).containsExactlyInAnyOrder("alunoId", "status");
        assertThat(ausencia.get("alunoId").asLong()).isEqualTo(2L);
        assertThat(ausencia.get("status").asString()).isEqualTo("FALTA");
    }

    @Test
    void gradePosted() {
        JsonNode json = serializar(KafkaTopics.GRADE_POSTED, new GradePostedEvent(
                9L, 12L, 3L, 1, List.of(new GradePostedEvent.NotaAluno(1L, new BigDecimal("8.50"))),
                Instant.parse("2026-10-24T11:00:00Z")));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "avaliacaoId", "turmaId", "disciplinaId", "bimestre", "notas", "occurredAt");
        assertThat(json.get("bimestre").asInt()).isEqualTo(1);
        JsonNode nota = json.get("notas").get(0);
        assertThat(nota.propertyNames()).containsExactlyInAnyOrder("alunoId", "valor");
        assertThat(nota.get("alunoId").asLong()).isEqualTo(1L);
        assertThat(nota.get("valor").decimalValue()).isEqualByComparingTo("8.50");
    }
}
