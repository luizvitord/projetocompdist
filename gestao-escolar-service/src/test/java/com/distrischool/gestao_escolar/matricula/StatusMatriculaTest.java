package com.distrischool.gestao_escolar.matricula;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static com.distrischool.gestao_escolar.matricula.StatusMatricula.ATIVA;
import static com.distrischool.gestao_escolar.matricula.StatusMatricula.CANCELADA;
import static com.distrischool.gestao_escolar.matricula.StatusMatricula.TRANCADA;
import static com.distrischool.gestao_escolar.matricula.StatusMatricula.TRANSFERIDA;
import static org.assertj.core.api.Assertions.assertThat;

/** Todas as 25 combinações de status anterior → novo. */
class StatusMatriculaTest {

    @ParameterizedTest(name = "{0} → {1}: {2}")
    @CsvSource({
            "ATIVA, ATIVA, false",
            "ATIVA, TRANCADA, true",
            "ATIVA, CANCELADA, true",
            "ATIVA, CONCLUIDA, true",
            "ATIVA, TRANSFERIDA, true",
            "TRANCADA, ATIVA, true",
            "TRANCADA, TRANCADA, false",
            "TRANCADA, CANCELADA, true",
            "TRANCADA, CONCLUIDA, false",
            "TRANCADA, TRANSFERIDA, false",
            "CANCELADA, ATIVA, false",
            "CANCELADA, TRANCADA, false",
            "CANCELADA, CANCELADA, false",
            "CANCELADA, CONCLUIDA, false",
            "CANCELADA, TRANSFERIDA, false",
            "CONCLUIDA, ATIVA, false",
            "CONCLUIDA, TRANCADA, false",
            "CONCLUIDA, CANCELADA, false",
            "CONCLUIDA, CONCLUIDA, false",
            "CONCLUIDA, TRANSFERIDA, false",
            "TRANSFERIDA, ATIVA, false",
            "TRANSFERIDA, TRANCADA, false",
            "TRANSFERIDA, CANCELADA, false",
            "TRANSFERIDA, CONCLUIDA, false",
            "TRANSFERIDA, TRANSFERIDA, false"
    })
    void transicoes(StatusMatricula anterior, StatusMatricula novo, boolean permitida) {
        assertThat(anterior.podeIrPara(novo)).isEqualTo(permitida);
    }

    @ParameterizedTest
    @EnumSource(StatusMatricula.class)
    void statusFinaisNaoTemSaida(StatusMatricula status) {
        boolean temSaida = false;
        for (StatusMatricula novo : StatusMatricula.values()) {
            temSaida |= status.podeIrPara(novo);
        }
        assertThat(status.isFinal()).isEqualTo(!temSaida);
    }

    @ParameterizedTest
    @EnumSource(StatusMatricula.class)
    void motivoObrigatorioParaTrancarCancelarETransferir(StatusMatricula status) {
        boolean esperado = status == TRANCADA || status == CANCELADA || status == TRANSFERIDA;
        assertThat(status.exigeMotivo()).isEqualTo(esperado);
    }

    @ParameterizedTest
    @EnumSource(StatusMatricula.class)
    void vigentesSaoAtivaETrancada(StatusMatricula status) {
        assertThat(StatusMatricula.VIGENTES.contains(status)).isEqualTo(status == ATIVA || status == TRANCADA);
    }
}
