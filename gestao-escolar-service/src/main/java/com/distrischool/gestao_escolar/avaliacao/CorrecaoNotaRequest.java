package com.distrischool.gestao_escolar.avaliacao;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CorrecaoNotaRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal valor,
        @NotBlank @Size(max = 500) String motivo
) {
}
