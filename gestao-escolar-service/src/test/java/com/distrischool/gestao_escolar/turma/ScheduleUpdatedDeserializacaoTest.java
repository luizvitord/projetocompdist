package com.distrischool.gestao_escolar.turma;

import com.distrischool.gestao_escolar.turma.ScheduleUpdatedMessage.Acao;
import com.distrischool.gestao_escolar.turma.ScheduleUpdatedMessage.DisciplinaOfertada;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.SerializationUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lê o schedule.updated com a mesma configuração do consumidor (application.properties +
 * {@code @KafkaListener}): sem cabeçalhos de tipo e sem as classes do produtor.
 */
class ScheduleUpdatedDeserializacaoTest {

    private static final String CONTRATO = """
            {
              "turmaId": 12,
              "codigo": "9A-2026",
              "nome": "9º Ano A",
              "anoLetivo": 2026,
              "turno": "MANHA",
              "capacidade": 35,
              "disciplinas": [
                { "disciplinaId": 3, "codigo": "MAT", "nome": "Matemática", "professorId": 7 }
              ],
              "acao": "ATUALIZADA",
              "occurredAt": "2026-10-24T11:00:00Z"
            }
            """;

    private Object desserializar(String json, RecordHeaders headers) {
        try (ErrorHandlingDeserializer<Object> deserializer = new ErrorHandlingDeserializer<>()) {
            deserializer.configure(Map.of(
                    "spring.deserializer.value.delegate.class",
                    "org.springframework.kafka.support.serializer.JacksonJsonDeserializer",
                    "spring.json.use.type.headers", "false",
                    "spring.json.value.default.type", ScheduleUpdatedMessage.class.getName()), false);
            return deserializer.deserialize("schedule.updated", headers, json.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void leOContratoSemCabecalhosDeTipo() {
        Object lido = desserializar(CONTRATO, new RecordHeaders());

        assertThat(lido).isEqualTo(new ScheduleUpdatedMessage(
                12L, "9A-2026", "9º Ano A", 2026, "MANHA", 35,
                java.util.List.of(new DisciplinaOfertada(3L, "MAT", "Matemática", 7L)),
                Acao.ATUALIZADA, Instant.parse("2026-10-24T11:00:00Z")));
    }

    @Test
    void ignoraCamposDesconhecidos() {
        String comCamposNovos = CONTRATO
                .replace("\"turmaId\": 12,", "\"turmaId\": 12, \"sala\": \"B-12\", \"professores\": [{\"id\": 7}],")
                .replace("\"professorId\": 7 }", "\"professorId\": 7, \"cargaHoraria\": 80 }");

        ScheduleUpdatedMessage lido = (ScheduleUpdatedMessage) desserializar(comCamposNovos, new RecordHeaders());

        assertThat(lido.turmaId()).isEqualTo(12L);
        assertThat(lido.disciplinas()).containsExactly(new DisciplinaOfertada(3L, "MAT", "Matemática", 7L));
    }

    @Test
    void capacidadeEProfessorNulos() {
        String json = CONTRATO.replace("\"capacidade\": 35", "\"capacidade\": null")
                .replace("\"professorId\": 7", "\"professorId\": null");

        ScheduleUpdatedMessage lido = (ScheduleUpdatedMessage) desserializar(json, new RecordHeaders());

        assertThat(lido.capacidade()).isNull();
        assertThat(lido.disciplinas().get(0).professorId()).isNull();
    }

    @Test
    void jsonInvalidoNaoLancaExcecaoEVaiParaOErrorHandler() {
        RecordHeaders headers = new RecordHeaders();

        Object lido = desserializar("{ isto não é json", headers);

        // O ErrorHandlingDeserializer devolve null e guarda a exceção em um cabeçalho; o
        // DefaultErrorHandler a reconhece e envia o registro (com os bytes originais) para a DLT
        assertThat(lido).isNull();
        assertThat(headers.lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER)).isNotNull();
    }
}
