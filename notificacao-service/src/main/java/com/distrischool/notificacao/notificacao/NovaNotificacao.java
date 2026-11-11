package com.distrischool.notificacao.notificacao;

/** Notificação ainda não gravada, gerada a partir de um evento. {@code link} é uma rota do frontend. */
public record NovaNotificacao(
        Destinatario destinatario,
        TipoNotificacao tipo,
        String titulo,
        String mensagem,
        String link
) {
}
