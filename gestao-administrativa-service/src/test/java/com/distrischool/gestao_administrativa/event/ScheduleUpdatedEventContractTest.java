package com.distrischool.gestao_administrativa.event;

import com.distrischool.gestao_administrativa.event.ScheduleUpdatedEvent.Acao;
import com.distrischool.gestao_administrativa.event.ScheduleUpdatedEvent.DisciplinaOfertada;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o formato de {@code schedule.updated}, que o gestao-escolar-service consome. Usa o mesmo
 * serializador configurado em application.properties, sem cabeçalhos de tipo Java.
 */
class ScheduleUpdatedEventContractTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private JsonNode serializar(ScheduleUpdatedEvent evento) {
        try (JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>()) {
            serializer.configure(Map.of("spring.json.add.type.headers", false), false);
            byte[] bytes = serializer.serialize("schedule.updated", evento);
            return mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    @Test
    void serializaOEstadoCompletoDaTurmaComOsNomesDoContrato() {
        ScheduleUpdatedEvent evento = new ScheduleUpdatedEvent(
                12L, "9A-2026", "9º Ano A", 2026, "MANHA", 35,
                List.of(new DisciplinaOfertada(3L, "MAT", "Matemática", 7L)),
                Acao.ATUALIZADA, Instant.parse("2026-10-24T11:00:00Z"));

        JsonNode json = serializar(evento);

        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "turmaId", "codigo", "nome", "anoLetivo", "turno", "capacidade",
                "disciplinas", "acao", "occurredAt");
        assertThat(json.get("turmaId").asLong()).isEqualTo(12L);
        assertThat(json.get("codigo").asString()).isEqualTo("9A-2026");
        assertThat(json.get("nome").asString()).isEqualTo("9º Ano A");
        assertThat(json.get("anoLetivo").asInt()).isEqualTo(2026);
        assertThat(json.get("turno").asString()).isEqualTo("MANHA");
        assertThat(json.get("capacidade").asInt()).isEqualTo(35);
        assertThat(json.get("acao").asString()).isEqualTo("ATUALIZADA");
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-10-24T11:00:00Z");

        JsonNode disciplina = json.get("disciplinas").get(0);
        assertThat(disciplina.propertyNames()).containsExactlyInAnyOrder("disciplinaId", "codigo", "nome", "professorId");
        assertThat(disciplina.get("disciplinaId").asLong()).isEqualTo(3L);
        assertThat(disciplina.get("codigo").asString()).isEqualTo("MAT");
        assertThat(disciplina.get("nome").asString()).isEqualTo("Matemática");
        assertThat(disciplina.get("professorId").asLong()).isEqualTo(7L);
    }

    @Test
    void capacidadeEProfessorNulosContinuamNoPayload() {
        ScheduleUpdatedEvent evento = new ScheduleUpdatedEvent(
                12L, "9A-2026", "9º Ano A", 2026, "MANHA", null,
                List.of(new DisciplinaOfertada(3L, "MAT", "Matemática", null)),
                Acao.CRIADA, Instant.parse("2026-10-24T11:00:00Z"));

        JsonNode json = serializar(evento);

        assertThat(json.has("capacidade")).isTrue();
        assertThat(json.get("capacidade").isNull()).isTrue();
        assertThat(json.get("disciplinas").get(0).get("professorId").isNull()).isTrue();
    }

    @Test
    void turmaSemDisciplinasTrazListaVazia() {
        ScheduleUpdatedEvent evento = new ScheduleUpdatedEvent(
                12L, "9A-2026", "9º Ano A", 2026, "MANHA", 35, List.of(),
                Acao.REMOVIDA, Instant.parse("2026-10-24T11:00:00Z"));

        JsonNode json = serializar(evento);

        assertThat(json.get("disciplinas").isArray()).isTrue();
        assertThat(json.get("disciplinas")).isEmpty();
        assertThat(json.get("acao").asString()).isEqualTo("REMOVIDA");
    }
}
