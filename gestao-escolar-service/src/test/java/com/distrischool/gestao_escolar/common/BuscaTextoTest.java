package com.distrischool.gestao_escolar.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuscaTextoTest {

    @Test
    void vazioOuSoEspacosViraSemFiltro() {
        assertThat(BuscaTexto.normalizar(null)).isNull();
        assertThat(BuscaTexto.normalizar("")).isNull();
        assertThat(BuscaTexto.normalizar(" \t ")).isNull();
        assertThat(BuscaTexto.normalizar("  Ana  ")).isEqualTo("Ana");
    }

    @Test
    void padraoEscapaCuringasEFicaMinusculo() {
        assertThat(BuscaTexto.padraoLike("Ana")).isEqualTo("%ana%");
        assertThat(BuscaTexto.padraoLike("50%_")).isEqualTo("%50\\%\\_%");
        assertThat(BuscaTexto.padraoLike("a\\b")).isEqualTo("%a\\\\b%");
    }
}
