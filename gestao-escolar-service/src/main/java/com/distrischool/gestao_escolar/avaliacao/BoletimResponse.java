package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.frequencia.ResumoFrequencia;
import com.distrischool.gestao_escolar.matricula.StatusMatricula;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/alunos/{alunoId}/boletim}: todas as matrículas do aluno no ano letivo
 * (inclusive transferidas), agrupadas por disciplina.
 */
public record BoletimResponse(
        Long alunoId,
        Integer anoLetivo,
        BigDecimal mediaAprovacao,
        BigDecimal frequenciaMinima,
        SituacaoBoletim situacao,
        ResumoFrequencia frequenciaGeral,
        List<MatriculaResumo> matriculas,
        List<Disciplina> disciplinas
) {

    public record MatriculaResumo(Long matriculaId, Long turmaId, String turmaCodigo, StatusMatricula status,
                                  LocalDate dataMatricula, LocalDate dataEncerramento) {
    }

    public record Disciplina(
            Long disciplinaId,
            String codigo,
            String nome,
            List<Bimestre> bimestres,
            BigDecimal mediaAnual,
            ResumoFrequencia frequencia,
            int avaliacoesPendentes,
            SituacaoBoletim situacao
    ) {
    }

    public record Bimestre(int bimestre, BigDecimal media, BigDecimal mediaSemRecuperacao, BigDecimal recuperacao,
                           int avaliacoesPendentes) {
    }
}
