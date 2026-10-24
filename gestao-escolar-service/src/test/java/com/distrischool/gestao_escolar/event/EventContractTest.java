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
}
