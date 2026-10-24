package com.distrischool.gestao_escolar.avaliacao;

/**
 * Situação do aluno (ou de uma disciplina) no boletim. Os quatro primeiros valores seguem a regra
 * de aprovação; os três últimos refletem uma matrícula que deixou de estar ativa sem ser concluída.
 */
public enum SituacaoBoletim {
    CURSANDO,
    APROVADO,
    REPROVADO_POR_NOTA,
    REPROVADO_POR_FALTA,
    TRANCADO,
    CANCELADO,
    TRANSFERIDO
}
