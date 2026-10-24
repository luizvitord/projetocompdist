package com.distrischool.gestao_administrativa.professor;

import java.time.Instant;

public record ProfessorResponse(
        Long id,
        String nome,
        String qualificacao,
        String email,
        String telefone,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProfessorResponse from(Professor professor) {
        return new ProfessorResponse(
                professor.getId(),
                professor.getNome(),
                professor.getQualificacao(),
                professor.getEmail(),
                professor.getTelefone(),
                professor.getCreatedAt(),
                professor.getUpdatedAt()
        );
    }
}
