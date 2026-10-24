package com.distrischool.gestao_escolar.frequencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** {@code GET /api/frequencias/relatorio}: frequência de cada aluno da turma no período. */
public record RelatorioFrequenciaResponse(
        Long turmaId,
        Long disciplinaId,
        LocalDate dataInicio,
        LocalDate dataFim,
        BigDecimal minimoPercentual,
        List<Aluno> alunos
) {

    public record Aluno(
            Long alunoId,
            String numeroMatricula,
            String nome,
            long totalAulas,
            long presencas,
            long faltas,
            long atrasos,
            BigDecimal percentual,
            boolean abaixoDoMinimo
    ) {
    }
}
