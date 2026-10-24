package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.matricula.StatusMatricula;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasAprovacaoTest {

    @Test
    void matriculaAtivaEstaCursandoMesmoComNotaEFrequenciaRuins() {
        assertThat(RegrasAprovacao.situacaoGeral(StatusMatricula.ATIVA, true, List.of(false)))
                .isEqualTo(SituacaoBoletim.CURSANDO);
        assertThat(RegrasAprovacao.situacaoDisciplina(StatusMatricula.ATIVA, true, false))
                .isEqualTo(SituacaoBoletim.CURSANDO);
    }

    @Test
    void concluidaComFrequenciaAbaixoReprovaPorFaltaMesmoComNotasBoas() {
        assertThat(RegrasAprovacao.situacaoGeral(StatusMatricula.CONCLUIDA, true, List.of(true, true)))
                .isEqualTo(SituacaoBoletim.REPROVADO_POR_FALTA);
        assertThat(RegrasAprovacao.situacaoDisciplina(StatusMatricula.CONCLUIDA, true, true))
                .isEqualTo(SituacaoBoletim.REPROVADO_POR_FALTA);
    }

    @Test
    void concluidaComTodasAsDisciplinasNaMediaEAprovada() {
        assertThat(RegrasAprovacao.situacaoGeral(StatusMatricula.CONCLUIDA, false, List.of(true, true, true)))
                .isEqualTo(SituacaoBoletim.APROVADO);
    }

    @Test
    void umaDisciplinaAbaixoDaMediaReprovaPorNota() {
        assertThat(RegrasAprovacao.situacaoGeral(StatusMatricula.CONCLUIDA, false, List.of(true, false, true)))
                .isEqualTo(SituacaoBoletim.REPROVADO_POR_NOTA);
        assertThat(RegrasAprovacao.situacaoDisciplina(StatusMatricula.CONCLUIDA, false, false))
                .isEqualTo(SituacaoBoletim.REPROVADO_POR_NOTA);
        assertThat(RegrasAprovacao.situacaoDisciplina(StatusMatricula.CONCLUIDA, false, true))
                .isEqualTo(SituacaoBoletim.APROVADO);
    }

    @Test
    void concluidaSemDisciplinasNaoAprova() {
        assertThat(RegrasAprovacao.situacaoGeral(StatusMatricula.CONCLUIDA, false, List.of()))
                .isEqualTo(SituacaoBoletim.REPROVADO_POR_NOTA);
    }

    @ParameterizedTest
    @CsvSource({"TRANCADA, TRANCADO", "CANCELADA, CANCELADO", "TRANSFERIDA, TRANSFERIDO"})
    void matriculaEncerradaSemConclusao(StatusMatricula status, SituacaoBoletim esperada) {
        assertThat(RegrasAprovacao.situacaoGeral(status, false, List.of(true))).isEqualTo(esperada);
        assertThat(RegrasAprovacao.situacaoDisciplina(status, false, true)).isEqualTo(esperada);
    }
}
