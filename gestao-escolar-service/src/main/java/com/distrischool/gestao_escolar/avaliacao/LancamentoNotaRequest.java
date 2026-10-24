package com.distrischool.gestao_escolar.avaliacao;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Item do lançamento em lote; o limite superior ({@code notaMaxima}) é validado no service. */
public record LancamentoNotaRequest(
        @NotNull Long matriculaId,
        @NotNull @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal valor
) {
}
