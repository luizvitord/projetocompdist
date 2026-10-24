package com.distrischool.gestao_escolar.frequencia;

public enum StatusPresenca {
    PRESENTE, FALTA, ATRASO;

    /** Atraso conta como presença no percentual de frequência. */
    public boolean contaComoPresenca() {
        return this != FALTA;
    }
}
