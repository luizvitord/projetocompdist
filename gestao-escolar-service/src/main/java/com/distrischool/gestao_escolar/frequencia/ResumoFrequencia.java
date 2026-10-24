package com.distrischool.gestao_escolar.frequencia;

import java.math.BigDecimal;

/**
 * Frequência consolidada. {@code percentual} é {@code null} quando não houve aula (nenhuma
 * chamada em que a matrícula constava).
 */
public record ResumoFrequencia(
        long totalAulas,
        long presencas,
        long faltas,
        long atrasos,
        BigDecimal percentual,
        boolean abaixoDoMinimo
) {
}
