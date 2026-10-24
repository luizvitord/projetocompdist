package com.distrischool.gestao_escolar.frequencia;

import java.math.BigDecimal;
import java.util.List;

/** {@code GET /api/frequencias/alunos/{alunoId}}: percentual por disciplina e geral no ano letivo. */
public record FrequenciaAlunoResponse(
        Long alunoId,
        Integer anoLetivo,
        BigDecimal minimoPercentual,
        List<Disciplina> disciplinas,
        ResumoFrequencia geral
) {

    public record Disciplina(Long disciplinaId, String codigo, String nome, ResumoFrequencia frequencia) {
    }
}
