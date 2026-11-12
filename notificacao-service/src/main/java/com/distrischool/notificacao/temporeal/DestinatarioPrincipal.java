package com.distrischool.notificacao.temporeal;

import com.distrischool.notificacao.notificacao.Destinatario;

import java.security.Principal;

/**
 * Usuário de uma sessão WebSocket: o destinatário lido do JWT do frame CONNECT. O nome
 * ({@code ALUNO:12}, {@code PROFESSOR:7}, {@code ADMIN}) é o que o
 * {@code convertAndSendToUser} usa para achar as sessões; todas as abas do mesmo destinatário (e
 * todos os administradores) têm o mesmo nome e recebem as mesmas notificações.
 */
public record DestinatarioPrincipal(Destinatario destinatario) implements Principal {

    @Override
    public String getName() {
        return destinatario.nome();
    }
}
