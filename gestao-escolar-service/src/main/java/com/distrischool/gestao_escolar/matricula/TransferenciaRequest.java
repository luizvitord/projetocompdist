package com.distrischool.gestao_escolar.matricula;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferenciaRequest(
        @NotNull Long turmaDestinoId,
        @NotBlank @Size(max = 500) String motivo
) {
}
