package com.distrischool.gestao_administrativa.turma;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TurmaRequest(
        @NotBlank @Size(max = 30) String codigo,
        @NotBlank @Size(max = 100) String nome,
        @NotNull @Min(2000) @Max(2100) Integer anoLetivo,
        @NotNull Turno turno,
        @Positive Integer capacidade
) {
}
