package com.distrischool.gestao_escolar.frequencia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculoFrequenciaTest {

    private final CalculoFrequencia calculo = new CalculoFrequencia(new BigDecimal("75"));

    @ParameterizedTest(name = "P={0} F={1} A={2} → {3}%")
    @CsvSource({
            "10, 0, 0, 100.00",
            "0, 10, 0, 0.00",
            "2, 1, 0, 66.67",
            "1, 2, 0, 33.33",
            "5, 3, 0, 62.50",
            "1, 1, 1, 66.67",
            "0, 0, 4, 100.00",
            "3, 1, 0, 75.00"
    })
    void percentualComDuasCasasHalfUp(long presencas, long faltas, long atrasos, String esperado) {
        ResumoFrequencia resumo = calculo.resumir(presencas, faltas, atrasos);

        assertThat(resumo.percentual()).isEqualByComparingTo(esperado);
        assertThat(resumo.percentual().scale()).isEqualTo(2);
        assertThat(resumo.totalAulas()).isEqualTo(presencas + faltas + atrasos);
    }

    @Test
    void atrasoContaComoPresenca() {
        ResumoFrequencia resumo = calculo.resumir(1, 1, 2);

        assertThat(resumo.percentual()).isEqualByComparingTo("75.00");
        assertThat(resumo.presencas()).isEqualTo(1);
        assertThat(resumo.atrasos()).isEqualTo(2);
        assertThat(resumo.faltas()).isEqualTo(1);
    }

    @Test
    void exatamenteNoMinimoNaoEstaAbaixo() {
        assertThat(calculo.resumir(3, 1, 0).abaixoDoMinimo()).isFalse();
    }

    @Test
    void abaixoDoMinimo() {
        assertThat(calculo.resumir(2, 1, 0).abaixoDoMinimo()).isTrue();
    }

    @Test
    void comparaOValorExatoENaoOArredondado() {
        // 18749 / 25000 = 74,996%: aparece como 75,00, mas está abaixo de 75
        ResumoFrequencia resumo = calculo.resumir(18_749, 6_251, 0);

        assertThat(resumo.percentual()).isEqualByComparingTo("75.00");
        assertThat(resumo.abaixoDoMinimo()).isTrue();
    }

    @Test
    void semAulasNaoTemPercentualNemFicaAbaixo() {
        ResumoFrequencia resumo = calculo.resumir(0, 0, 0);

        assertThat(resumo.totalAulas()).isZero();
        assertThat(resumo.percentual()).isNull();
        assertThat(resumo.abaixoDoMinimo()).isFalse();
    }

    @Test
    void minimoConfiguravel() {
        CalculoFrequencia exigente = new CalculoFrequencia(new BigDecimal("90"));

        assertThat(exigente.resumir(17, 3, 0).abaixoDoMinimo()).isTrue();
        assertThat(exigente.resumir(18, 2, 0).abaixoDoMinimo()).isFalse();
    }

    @Test
    void geralSomaAsAulasDeTodasAsDisciplinas() {
        ResumoFrequencia matematica = calculo.resumir(9, 1, 0);
        ResumoFrequencia portugues = calculo.resumir(1, 1, 0);

        ResumoFrequencia geral = calculo.somar(List.of(matematica, portugues));

        // (9 + 1) / 12, e não a média dos percentuais (90% e 50%)
        assertThat(geral.totalAulas()).isEqualTo(12);
        assertThat(geral.percentual()).isEqualByComparingTo("83.33");
        assertThat(geral.abaixoDoMinimo()).isFalse();
    }

    @Test
    void geralSemDisciplinas() {
        assertThat(calculo.somar(List.of()).percentual()).isNull();
    }

    @Test
    void rejeitaContagemNegativaEMinimoForaDaFaixa() {
        assertThatThrownBy(() -> calculo.resumir(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalculoFrequencia(new BigDecimal("101"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalculoFrequencia(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }
}
