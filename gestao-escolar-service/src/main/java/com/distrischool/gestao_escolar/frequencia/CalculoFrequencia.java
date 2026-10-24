package com.distrischool.gestao_escolar.frequencia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Cálculo do percentual de presença, sem dependência do Spring:
 * (PRESENTE + ATRASO) / total de chamadas em que a matrícula constava × 100, com 2 casas
 * ({@code HALF_UP}). A comparação com o mínimo usa o valor exato, não o arredondado: 74,996%
 * aparece como 75,00 mas continua abaixo de 75.
 */
public final class CalculoFrequencia {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final BigDecimal minimoPercentual;

    public CalculoFrequencia(BigDecimal minimoPercentual) {
        if (minimoPercentual == null || minimoPercentual.signum() < 0 || minimoPercentual.compareTo(CEM) > 0) {
            throw new IllegalArgumentException("Frequência mínima deve estar entre 0 e 100: " + minimoPercentual);
        }
        this.minimoPercentual = minimoPercentual;
    }

    public BigDecimal getMinimoPercentual() {
        return minimoPercentual;
    }

    public ResumoFrequencia resumir(long presencas, long faltas, long atrasos) {
        if (presencas < 0 || faltas < 0 || atrasos < 0) {
            throw new IllegalArgumentException("Contagens não podem ser negativas");
        }
        long total = presencas + faltas + atrasos;
        if (total == 0) {
            return new ResumoFrequencia(0, 0, 0, 0, null, false);
        }
        BigDecimal presentes = BigDecimal.valueOf(presencas + atrasos);
        BigDecimal percentual = presentes.multiply(CEM).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        // presentes × 100 < mínimo × total  ⇔  percentual exato < mínimo
        boolean abaixo = presentes.multiply(CEM).compareTo(minimoPercentual.multiply(BigDecimal.valueOf(total))) < 0;
        return new ResumoFrequencia(total, presencas, faltas, atrasos, percentual, abaixo);
    }

    /** Frequência geral: soma as aulas de todas as partes (cada aula pesa igual). */
    public ResumoFrequencia somar(Collection<ResumoFrequencia> partes) {
        long presencas = 0;
        long faltas = 0;
        long atrasos = 0;
        for (ResumoFrequencia parte : partes) {
            presencas += parte.presencas();
            faltas += parte.faltas();
            atrasos += parte.atrasos();
        }
        return resumir(presencas, faltas, atrasos);
    }
}
