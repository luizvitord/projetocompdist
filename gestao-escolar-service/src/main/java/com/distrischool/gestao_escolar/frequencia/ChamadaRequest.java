package com.distrischool.gestao_escolar.frequencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Corpo de criação e de edição; na edição os registros são substituídos. */
public record ChamadaRequest(
        @NotNull Long turmaId,
        @NotNull Long disciplinaId,
        @NotNull @PastOrPresent LocalDate data,
        Long professorId,
        @NotEmpty @Valid List<Registro> registros
) {

    public record Registro(
            @NotNull Long matriculaId,
            @NotNull StatusPresenca status,
            @Size(max = 255) String observacao
    ) {
    }
}
