package com.distrischool.gestao_escolar.aluno;

import java.time.Instant;
import java.time.LocalDate;

public record AlunoResponse(
        Long id,
        String matricula,
        String nome,
        LocalDate dataNascimento,
        String endereco,
        String telefone,
        String email,
        Instant createdAt,
        Instant updatedAt
) {

    public static AlunoResponse from(Aluno aluno) {
        return new AlunoResponse(
                aluno.getId(),
                aluno.getMatricula(),
                aluno.getNome(),
                aluno.getDataNascimento(),
                aluno.getEndereco(),
                aluno.getTelefone(),
                aluno.getEmail(),
                aluno.getCreatedAt(),
                aluno.getUpdatedAt()
        );
    }
}
