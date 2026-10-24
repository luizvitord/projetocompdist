package com.distrischool.gestao_escolar.matricula;

import java.time.Instant;
import java.time.LocalDate;

public record MatriculaResponse(
        Long id,
        Long alunoId,
        Long turmaId,
        Integer anoLetivo,
        StatusMatricula status,
        LocalDate dataMatricula,
        LocalDate dataEncerramento,
        String motivo,
        Long matriculaOrigemId,
        Instant createdAt,
        Instant updatedAt
) {

    public static MatriculaResponse from(Matricula matricula) {
        return new MatriculaResponse(
                matricula.getId(),
                matricula.getAlunoId(),
                matricula.getTurmaId(),
                matricula.getAnoLetivo(),
                matricula.getStatus(),
                matricula.getDataMatricula(),
                matricula.getDataEncerramento(),
                matricula.getMotivo(),
                matricula.getMatriculaOrigemId(),
                matricula.getCreatedAt(),
                matricula.getUpdatedAt()
        );
    }
}
