package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.avaliacao.CalculoMedias.MediaBimestre;
import com.distrischool.gestao_escolar.avaliacao.CalculoMedias.NotaAvaliacao;
import com.distrischool.gestao_escolar.avaliacao.CalculoMedias.ResultadoDisciplina;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static com.distrischool.gestao_escolar.avaliacao.TipoAvaliacao.ATIVIDADE;
import static com.distrischool.gestao_escolar.avaliacao.TipoAvaliacao.PROVA;
import static com.distrischool.gestao_escolar.avaliacao.TipoAvaliacao.RECUPERACAO;
import static com.distrischool.gestao_escolar.avaliacao.TipoAvaliacao.TRABALHO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculoMediasTest {

    private final CalculoMedias calculo = new CalculoMedias(new BigDecimal("6.0"));

    private static NotaAvaliacao nota(int bimestre, TipoAvaliacao tipo, String peso, String notaMaxima, String valor) {
        return new NotaAvaliacao(bimestre, tipo, new BigDecimal(peso), new BigDecimal(notaMaxima),
                valor == null ? null : new BigDecimal(valor));
    }

    private MediaBimestre bimestre1(NotaAvaliacao... notas) {
        return calculo.calcular(List.of(notas)).bimestres().get(0);
    }

    // --- normalização ---

    @ParameterizedTest(name = "{0} de {1} → {2}")
    @CsvSource({
            "10, 10, 10",
            "5, 10, 5",
            "2.5, 5, 5",
            "15, 20, 7.5",
            "0, 10, 0",
            "7, 7, 10",
            "1, 3, 3.3333333333"
    })
    void normalizaParaAEscalaDeZeroADez(String valor, String notaMaxima, String esperado) {
        assertThat(CalculoMedias.normalizar(new BigDecimal(valor), new BigDecimal(notaMaxima)))
                .isEqualByComparingTo(esperado);
    }

    // --- média do bimestre ---

    @Test
    void mediaPonderadaPeloPeso() {
        // (4 × 2 + 10 × 1) / 3 = 6
        MediaBimestre b = bimestre1(nota(1, PROVA, "2", "10", "4"), nota(1, TRABALHO, "1", "10", "10"));

        assertThat(b.media()).isEqualByComparingTo("6.00");
        assertThat(b.mediaSemRecuperacao()).isEqualByComparingTo("6.00");
        assertThat(b.recuperacao()).isNull();
    }

    @Test
    void notasDeEscalasDiferentesSaoNormalizadasAntesDaMedia() {
        // prova 4,5/10 com peso 2 e trabalho 2,5/5 (= 5,0) com peso 1: (9 + 5) / 3 = 4,666…
        MediaBimestre b = bimestre1(nota(1, PROVA, "2", "10", "4.5"), nota(1, TRABALHO, "1", "5", "2.5"));

        assertThat(b.media()).isEqualByComparingTo("4.67");
    }

    @Test
    void resultadoComDuasCasasHalfUp() {
        // (7 + 8 + 8) / 3 = 7,666… → 7,67; (6 + 6 + 7) / 3 = 6,333… → 6,33; 6,125 → 6,13
        assertThat(bimestre1(nota(1, PROVA, "1", "10", "7"), nota(1, PROVA, "1", "10", "8"),
                nota(1, PROVA, "1", "10", "8")).media()).isEqualByComparingTo("7.67");
        assertThat(bimestre1(nota(1, PROVA, "1", "10", "6"), nota(1, PROVA, "1", "10", "6"),
                nota(1, PROVA, "1", "10", "7")).media()).isEqualByComparingTo("6.33");
        assertThat(bimestre1(nota(1, PROVA, "1", "10", "6.25"), nota(1, PROVA, "1", "10", "6")).media())
                .isEqualByComparingTo("6.13");
        assertThat(bimestre1(nota(1, PROVA, "1", "10", "6")).media().scale()).isEqualTo(2);
    }

    @Test
    void recuperacaoMaiorSubstituiAMedia() {
        MediaBimestre b = bimestre1(nota(1, PROVA, "1", "10", "4"), nota(1, RECUPERACAO, "1", "10", "7"));

        assertThat(b.mediaSemRecuperacao()).isEqualByComparingTo("4.00");
        assertThat(b.recuperacao()).isEqualByComparingTo("7.00");
        assertThat(b.media()).isEqualByComparingTo("7.00");
    }

    @Test
    void recuperacaoMenorNaoBaixaAMedia() {
        MediaBimestre b = bimestre1(nota(1, PROVA, "1", "10", "8"), nota(1, RECUPERACAO, "1", "10", "5"));

        assertThat(b.media()).isEqualByComparingTo("8.00");
    }

    @Test
    void recuperacaoNaoEntraNaMediaPonderadaENemOPesoDelaConta() {
        MediaBimestre b = bimestre1(nota(1, PROVA, "1", "10", "4"), nota(1, RECUPERACAO, "5", "10", "6"));

        assertThat(b.mediaSemRecuperacao()).isEqualByComparingTo("4.00");
        assertThat(b.media()).isEqualByComparingTo("6.00");
    }

    @Test
    void recuperacaoTambemENormalizada() {
        MediaBimestre b = bimestre1(nota(1, PROVA, "1", "10", "3"), nota(1, RECUPERACAO, "1", "20", "13"));

        assertThat(b.recuperacao()).isEqualByComparingTo("6.50");
        assertThat(b.media()).isEqualByComparingTo("6.50");
    }

    @Test
    void soRecuperacaoLancadaViraAMediaDoBimestre() {
        MediaBimestre b = bimestre1(nota(1, PROVA, "1", "10", null), nota(1, RECUPERACAO, "1", "10", "6"));

        assertThat(b.mediaSemRecuperacao()).isNull();
        assertThat(b.media()).isEqualByComparingTo("6.00");
        assertThat(b.avaliacoesPendentes()).isEqualTo(1);
    }

    @Test
    void avaliacaoSemNotaNaoEntraNoCalculoEContaComoPendente() {
        MediaBimestre b = bimestre1(nota(1, PROVA, "2", "10", "8"), nota(1, TRABALHO, "1", "10", null),
                nota(1, RECUPERACAO, "1", "10", null));

        assertThat(b.media()).isEqualByComparingTo("8.00");
        assertThat(b.avaliacoesPendentes()).isEqualTo(2);
    }

    @Test
    void bimestreSemNotasNaoTemMedia() {
        MediaBimestre b = bimestre1(nota(1, PROVA, "1", "10", null));

        assertThat(b.media()).isNull();
        assertThat(b.mediaSemRecuperacao()).isNull();
        assertThat(b.avaliacoesPendentes()).isEqualTo(1);
    }

    @Test
    void notaZeroContaNaMedia() {
        assertThat(bimestre1(nota(1, PROVA, "1", "10", "0"), nota(1, ATIVIDADE, "1", "10", "10")).media())
                .isEqualByComparingTo("5.00");
    }

    // --- média anual ---

    @Test
    void mediaAnualEAMediaAritmeticaDosBimestresComMedia() {
        ResultadoDisciplina r = calculo.calcular(List.of(
                nota(1, PROVA, "1", "10", "6"),
                nota(2, PROVA, "1", "10", "7"),
                nota(3, PROVA, "1", "10", "9"),
                nota(4, PROVA, "1", "10", null)));

        // 4º bimestre sem média fica de fora: (6 + 7 + 9) / 3 = 7,333…
        assertThat(r.mediaAnual()).isEqualByComparingTo("7.33");
        assertThat(r.bimestres()).extracting(MediaBimestre::bimestre).containsExactly(1, 2, 3, 4);
        assertThat(r.bimestres().get(3).media()).isNull();
        assertThat(r.avaliacoesPendentes()).isEqualTo(1);
    }

    @Test
    void mediaAnualUsaAsMediasArredondadasDosBimestres() {
        // 1º bimestre: 6,005 exato, mostrado como 6,01; 2º: 6,00.
        // Com as médias mostradas: (6,01 + 6,00) / 2 = 6,005 → 6,01 (com as exatas daria 6,0025 → 6,00)
        ResultadoDisciplina r = calculo.calcular(List.of(
                nota(1, PROVA, "1", "10", "6.01"), nota(1, PROVA, "1", "10", "6.00"),
                nota(2, PROVA, "1", "10", "6")));

        assertThat(r.bimestres().get(0).media()).isEqualByComparingTo("6.01");
        assertThat(r.bimestres().get(1).media()).isEqualByComparingTo("6.00");
        assertThat(r.mediaAnual()).isEqualByComparingTo("6.01");
    }

    @Test
    void mediaAnualUsaAMediaFinalComRecuperacao() {
        ResultadoDisciplina r = calculo.calcular(List.of(
                nota(1, PROVA, "1", "10", "4"), nota(1, RECUPERACAO, "1", "10", "8"),
                nota(2, PROVA, "1", "10", "6")));

        assertThat(r.mediaAnual()).isEqualByComparingTo("7.00");
    }

    @Test
    void semNenhumaNotaNaoHaMediaAnual() {
        ResultadoDisciplina r = calculo.calcular(List.of());

        assertThat(r.mediaAnual()).isNull();
        assertThat(r.atingiuMedia()).isFalse();
        assertThat(r.bimestres()).hasSize(4).allSatisfy(b -> assertThat(b.media()).isNull());
    }

    // --- aprovação ---

    @Test
    void mediaMinimaInclusiva() {
        assertThat(calculo.atingiu(new BigDecimal("6.00"))).isTrue();
        assertThat(calculo.atingiu(new BigDecimal("5.99"))).isFalse();
        assertThat(calculo.atingiu(null)).isFalse();
    }

    @Test
    void mediaMinimaConfiguravel() {
        CalculoMedias exigente = new CalculoMedias(new BigDecimal("7"));

        assertThat(exigente.calcular(List.of(nota(1, PROVA, "1", "10", "6.5"))).atingiuMedia()).isFalse();
        assertThat(exigente.calcular(List.of(nota(1, PROVA, "1", "10", "7"))).atingiuMedia()).isTrue();
    }

    // --- entradas inválidas ---

    @Test
    void rejeitaEntradasInvalidas() {
        assertThatThrownBy(() -> nota(5, PROVA, "1", "10", "5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nota(0, PROVA, "1", "10", "5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nota(1, PROVA, "0", "10", "5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nota(1, PROVA, "1", "0", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nota(1, PROVA, "1", "10", "-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nota(1, PROVA, "1", "10", "10.01")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalculoMedias(new BigDecimal("11"))).isInstanceOf(IllegalArgumentException.class);
    }
}
