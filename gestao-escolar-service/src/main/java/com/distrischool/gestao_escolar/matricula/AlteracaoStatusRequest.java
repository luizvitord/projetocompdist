package com.distrischool.gestao_escolar.matricula;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de {@code PATCH /api/matriculas/{id}/status}; o motivo é obrigatório conforme o status. */
public record AlteracaoStatusRequest(
        @NotNull StatusMatricula status,
        @Size(max = 500) String motivo
) {
}
