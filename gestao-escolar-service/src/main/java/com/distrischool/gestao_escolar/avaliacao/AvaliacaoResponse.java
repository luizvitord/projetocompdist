package com.distrischool.gestao_escolar.avaliacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AvaliacaoResponse(
        Long id,
        Long turmaId,
        Long disciplinaId,
        Integer bimestre,
        TipoAvaliacao tipo,
        String descricao,
        BigDecimal peso,
        BigDecimal notaMaxima,
        LocalDate data,
        Instant createdAt,
        Instant updatedAt
) {

    public static AvaliacaoResponse from(Avaliacao avaliacao) {
        return new AvaliacaoResponse(
                avaliacao.getId(),
                avaliacao.getTurmaId(),
                avaliacao.getDisciplinaId(),
                avaliacao.getBimestre(),
                avaliacao.getTipo(),
                avaliacao.getDescricao(),
                avaliacao.getPeso(),
                avaliacao.getNotaMaxima(),
                avaliacao.getData(),
                avaliacao.getCreatedAt(),
                avaliacao.getUpdatedAt()
        );
    }
}
