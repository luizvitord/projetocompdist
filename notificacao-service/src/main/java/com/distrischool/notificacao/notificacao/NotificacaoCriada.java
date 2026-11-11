package com.distrischool.notificacao.notificacao;

import java.time.Instant;

/** Notificação que acabou de ser gravada (o evento não era uma reentrega). */
public record NotificacaoCriada(
        Long id,
        Destinatario destinatario,
        TipoNotificacao tipo,
        String titulo,
        String mensagem,
        String link,
        Instant criadaEm
) {
}
