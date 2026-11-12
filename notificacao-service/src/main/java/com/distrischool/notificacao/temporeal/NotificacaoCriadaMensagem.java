package com.distrischool.notificacao.temporeal;

import com.distrischool.notificacao.notificacao.Destinatario;
import com.distrischool.notificacao.notificacao.NotificacaoCriada;
import com.distrischool.notificacao.notificacao.Perfil;

/**
 * Evento interno do tópico {@code notificacao.criada}: avisa todas as réplicas de que há uma
 * notificação nova para um destinatário. Traz só o id e o destinatário, nunca o texto: a réplica
 * que tiver o usuário conectado busca a notificação no banco.
 */
public record NotificacaoCriadaMensagem(Long id, Perfil perfil, Long referenciaId) {

    public static NotificacaoCriadaMensagem de(NotificacaoCriada criada) {
        return new NotificacaoCriadaMensagem(criada.id(), criada.destinatario().perfil(),
                criada.destinatario().referenciaId());
    }

    public Destinatario destinatario() {
        return Destinatario.de(perfil, referenciaId);
    }
}
