package com.distrischool.notificacao.evento;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EventoHashTest {

    private static byte[] bytes(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void mesmoTopicoChaveEPayloadGeramOMesmoHashDe64CaracteresHex() {
        String hash = EventoHash.calcular("grade.posted", "12", bytes("{\"turmaId\":12}"));

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(EventoHash.calcular("grade.posted", "12", bytes("{\"turmaId\":12}"))).isEqualTo(hash);
    }

    @Test
    void qualquerDiferencaMudaOHash() {
        String hash = EventoHash.calcular("grade.posted", "12", bytes("{\"turmaId\":12}"));

        assertThat(EventoHash.calcular("attendance.recorded", "12", bytes("{\"turmaId\":12}"))).isNotEqualTo(hash);
        assertThat(EventoHash.calcular("grade.posted", "13", bytes("{\"turmaId\":12}"))).isNotEqualTo(hash);
        // Só o espaço muda: o hash é do JSON bruto, não do conteúdo interpretado
        assertThat(EventoHash.calcular("grade.posted", "12", bytes("{\"turmaId\": 12}"))).isNotEqualTo(hash);
    }

    @Test
    void asPartesNaoSeMisturam() {
        assertThat(EventoHash.calcular("a", "bc", bytes("d")))
                .isNotEqualTo(EventoHash.calcular("ab", "c", bytes("d")))
                .isNotEqualTo(EventoHash.calcular("a", "b", bytes("cd")));
    }

    @Test
    void chaveNulaEquivaleAChaveVazia() {
        assertThat(EventoHash.calcular("grade.posted", null, bytes("{}")))
                .isEqualTo(EventoHash.calcular("grade.posted", "", bytes("{}")));
    }
}
