package com.distrischool.gestao_escolar.avaliacao;

import java.math.BigDecimal;
import java.time.Instant;

public record NotaResponse(
        Long id,
        Long avaliacaoId,
        Long matriculaId,
        BigDecimal valor,
        Instant createdAt,
        Instant updatedAt
) {

    public static NotaResponse from(Nota nota) {
        return new NotaResponse(
                nota.getId(),
                nota.getAvaliacao().getId(),
                nota.getMatriculaId(),
                nota.getValor(),
                nota.getCreatedAt(),
                nota.getUpdatedAt()
        );
    }
}
