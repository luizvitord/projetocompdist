package com.distrischool.gestao_escolar.matricula;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MatriculaRequest(
        @NotBlank String matriculaAluno,
        @NotNull Long turmaId
) {
}
