package com.distrischool.gestao_escolar.matricula;

import java.time.Instant;
import java.time.LocalDate;

public record MatriculaResponse(
        Long id,
        Long alunoId,
        String matriculaAluno,
        String alunoNome,
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

    public MatriculaResponse(Long id, Long alunoId, Long turmaId, Integer anoLetivo, StatusMatricula status,
                             LocalDate dataMatricula, LocalDate dataEncerramento, String motivo,
                             Long matriculaOrigemId, Instant createdAt, Instant updatedAt) {
        this(id, alunoId, null, null, turmaId, anoLetivo, status, dataMatricula, dataEncerramento, motivo,
                matriculaOrigemId, createdAt, updatedAt);
    }

    public static MatriculaResponse from(Matricula matricula, String matriculaAluno, String alunoNome) {
        return new MatriculaResponse(
                matricula.getId(),
                matricula.getAlunoId(),
                matriculaAluno,
                alunoNome,
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
