package com.distrischool.gestao_escolar.matricula;

import jakarta.validation.constraints.NotNull;

public record MatriculaRequest(
        @NotNull Long alunoId,
        @NotNull Long turmaId
) {
}
