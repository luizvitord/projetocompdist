package com.distrischool.gestao_escolar.avaliacao;

import java.math.BigDecimal;
import java.time.Instant;

public record NotaHistoricoResponse(
        Long id,
        Long notaId,
        BigDecimal valorAnterior,
        BigDecimal valorNovo,
        String motivo,
        Instant alteradoEm
) {

    public static NotaHistoricoResponse from(NotaHistorico historico) {
        return new NotaHistoricoResponse(
                historico.getId(),
                historico.getNotaId(),
                historico.getValorAnterior(),
                historico.getValorNovo(),
                historico.getMotivo(),
                historico.getAlteradoEm()
        );
    }
}
