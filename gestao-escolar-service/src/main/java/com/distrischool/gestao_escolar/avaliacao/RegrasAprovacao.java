package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.matricula.StatusMatricula;

import java.util.Collection;

/**
 * Situação no boletim a partir do status da matrícula atual do aluno no ano (a mais recente),
 * sem dependência do Spring:
 * <ul>
 *   <li>{@code ATIVA}: {@code CURSANDO} (médias parciais);</li>
 *   <li>{@code CONCLUIDA}: {@code REPROVADO_POR_FALTA} se a frequência geral estiver abaixo do
 *       mínimo; senão {@code APROVADO} se todas as disciplinas atingirem a média mínima; senão
 *       {@code REPROVADO_POR_NOTA};</li>
 *   <li>{@code TRANCADA}, {@code CANCELADA} e {@code TRANSFERIDA} (saída para outra escola):
 *       {@code TRANCADO}, {@code CANCELADO} e {@code TRANSFERIDO}.</li>
 * </ul>
 */
public final class RegrasAprovacao {

    private RegrasAprovacao() {
    }

    public static SituacaoBoletim situacaoGeral(StatusMatricula statusAtual, boolean frequenciaGeralAbaixoDoMinimo,
                                                Collection<Boolean> disciplinasAtingiramAMedia) {
        if (statusAtual != StatusMatricula.CONCLUIDA) {
            return semConclusao(statusAtual);
        }
        if (frequenciaGeralAbaixoDoMinimo) {
            return SituacaoBoletim.REPROVADO_POR_FALTA;
        }
        boolean todas = !disciplinasAtingiramAMedia.isEmpty()
                && disciplinasAtingiramAMedia.stream().allMatch(Boolean::booleanValue);
        return todas ? SituacaoBoletim.APROVADO : SituacaoBoletim.REPROVADO_POR_NOTA;
    }

    /** A mesma regra aplicada a uma disciplina: a frequência considerada continua sendo a geral. */
    public static SituacaoBoletim situacaoDisciplina(StatusMatricula statusAtual, boolean frequenciaGeralAbaixoDoMinimo,
                                                     boolean atingiuAMedia) {
        if (statusAtual != StatusMatricula.CONCLUIDA) {
            return semConclusao(statusAtual);
        }
        if (frequenciaGeralAbaixoDoMinimo) {
            return SituacaoBoletim.REPROVADO_POR_FALTA;
        }
        return atingiuAMedia ? SituacaoBoletim.APROVADO : SituacaoBoletim.REPROVADO_POR_NOTA;
    }

    private static SituacaoBoletim semConclusao(StatusMatricula statusAtual) {
        return switch (statusAtual) {
            case ATIVA -> SituacaoBoletim.CURSANDO;
            case TRANCADA -> SituacaoBoletim.TRANCADO;
            case CANCELADA -> SituacaoBoletim.CANCELADO;
            case TRANSFERIDA -> SituacaoBoletim.TRANSFERIDO;
            case CONCLUIDA -> throw new IllegalStateException();
        };
    }
}
