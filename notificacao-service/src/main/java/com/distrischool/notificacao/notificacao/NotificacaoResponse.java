package com.distrischool.notificacao.notificacao;

import java.time.Instant;

public record NotificacaoResponse(
        Long id,
        TipoNotificacao tipo,
        String titulo,
        String mensagem,
        String link,
        Instant criadaEm,
        Instant lidaEm,
        boolean lida
) {

    public static NotificacaoResponse from(Notificacao n) {
        return new NotificacaoResponse(n.getId(), n.getTipo(), n.getTitulo(), n.getMensagem(), n.getLink(),
                n.getCriadaEm(), n.getLidaEm(), n.isLida());
    }

    public static NotificacaoResponse from(NotificacaoCriada n) {
        return new NotificacaoResponse(n.id(), n.tipo(), n.titulo(), n.mensagem(), n.link(), n.criadaEm(), null, false);
    }
}
