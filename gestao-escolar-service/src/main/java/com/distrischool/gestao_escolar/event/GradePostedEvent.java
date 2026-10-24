package com.distrischool.gestao_escolar.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Publicado no lançamento em lote (todas as notas do lote) e em cada correção (uma nota). */
public record GradePostedEvent(
        Long avaliacaoId,
        Long turmaId,
        Long disciplinaId,
        Integer bimestre,
        List<NotaAluno> notas,
        Instant occurredAt
) {

    public record NotaAluno(Long alunoId, BigDecimal valor) {
    }
}
