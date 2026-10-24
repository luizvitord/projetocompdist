package com.distrischool.gestao_administrativa.disciplina;

import java.time.Instant;

public record DisciplinaResponse(
        Long id,
        String codigo,
        String nome,
        Integer cargaHoraria,
        boolean ativa,
        Instant createdAt,
        Instant updatedAt
) {

    public static DisciplinaResponse from(Disciplina disciplina) {
        return new DisciplinaResponse(
                disciplina.getId(),
                disciplina.getCodigo(),
                disciplina.getNome(),
                disciplina.getCargaHoraria(),
                disciplina.isAtiva(),
                disciplina.getCreatedAt(),
                disciplina.getUpdatedAt()
        );
    }
}
