package com.distrischool.gestao_escolar.avaliacao;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code peso} e {@code notaMaxima} são opcionais (padrões 1 e 10). */
public record AvaliacaoRequest(
        @NotNull Long turmaId,
        @NotNull Long disciplinaId,
        @NotNull @Min(1) @Max(4) Integer bimestre,
        @NotNull TipoAvaliacao tipo,
        @Size(max = 255) String descricao,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 3, fraction = 2) BigDecimal peso,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 3, fraction = 2) BigDecimal notaMaxima,
        @NotNull LocalDate data
) {

    static final BigDecimal PESO_PADRAO = BigDecimal.ONE;
    static final BigDecimal NOTA_MAXIMA_PADRAO = BigDecimal.TEN;

    BigDecimal pesoOuPadrao() {
        return peso != null ? peso : PESO_PADRAO;
    }

    BigDecimal notaMaximaOuPadrao() {
        return notaMaxima != null ? notaMaxima : NOTA_MAXIMA_PADRAO;
    }
}
