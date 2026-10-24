package com.distrischool.gestao_administrativa.tecnico;

import java.time.Instant;
import java.time.LocalDate;

public record TecnicoResponse(
        Long id,
        String nome,
        String cpf,
        Cargo cargo,
        String setor,
        String email,
        String telefone,
        LocalDate dataAdmissao,
        boolean ativo,
        Instant createdAt,
        Instant updatedAt
) {

    public static TecnicoResponse from(Tecnico tecnico) {
        return new TecnicoResponse(
                tecnico.getId(),
                tecnico.getNome(),
                tecnico.getCpf(),
                tecnico.getCargo(),
                tecnico.getSetor(),
                tecnico.getEmail(),
                tecnico.getTelefone(),
                tecnico.getDataAdmissao(),
                tecnico.isAtivo(),
                tecnico.getCreatedAt(),
                tecnico.getUpdatedAt()
        );
    }
}
