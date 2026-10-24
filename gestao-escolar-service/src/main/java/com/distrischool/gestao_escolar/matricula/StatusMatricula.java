package com.distrischool.gestao_escolar.matricula;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida da matrícula:
 * <pre>
 * ATIVA    → TRANCADA, CANCELADA, CONCLUIDA, TRANSFERIDA
 * TRANCADA → ATIVA, CANCELADA
 * CANCELADA, CONCLUIDA, TRANSFERIDA: finais
 * </pre>
 */
public enum StatusMatricula {
    ATIVA, TRANCADA, CANCELADA, CONCLUIDA, TRANSFERIDA;

    /** Status que ocupam o "lugar" do aluno no ano letivo (no máximo uma matrícula por ano). */
    public static final Set<StatusMatricula> VIGENTES = EnumSet.of(ATIVA, TRANCADA);

    public boolean podeIrPara(StatusMatricula novo) {
        return switch (this) {
            case ATIVA -> novo == TRANCADA || novo == CANCELADA || novo == CONCLUIDA || novo == TRANSFERIDA;
            case TRANCADA -> novo == ATIVA || novo == CANCELADA;
            case CANCELADA, CONCLUIDA, TRANSFERIDA -> false;
        };
    }

    public boolean isFinal() {
        return this == CANCELADA || this == CONCLUIDA || this == TRANSFERIDA;
    }

    /** Status cuja transição exige um motivo. */
    public boolean exigeMotivo() {
        return this == TRANCADA || this == CANCELADA || this == TRANSFERIDA;
    }
}
