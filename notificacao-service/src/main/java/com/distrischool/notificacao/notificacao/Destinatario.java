package com.distrischool.notificacao.notificacao;

import java.util.Objects;

/**
 * Quem recebe uma notificação: o par (perfil, referenciaId), o mesmo que vai no JWT.
 * <p>
 * {@code referenciaId} é o id do professor (PROFESSOR) ou do aluno (ALUNO e RESPONSAVEL). ADMIN
 * representa a coordenação inteira e nunca tem referência: todos os administradores veem as mesmas
 * notificações, mesmo que o token de algum deles traga um {@code referenciaId}.
 */
public record Destinatario(Perfil perfil, Long referenciaId) {

    public Destinatario {
        Objects.requireNonNull(perfil, "perfil");
        if (perfil == Perfil.ADMIN) {
            referenciaId = null;
        }
    }

    public static Destinatario admin() {
        return new Destinatario(Perfil.ADMIN, null);
    }

    public static Destinatario de(Perfil perfil, Long referenciaId) {
        return new Destinatario(perfil, referenciaId);
    }

    /** Identificação sem dados pessoais: usada em logs e como nome do usuário da sessão WebSocket. */
    public String nome() {
        return referenciaId == null ? perfil.name() : perfil.name() + ":" + referenciaId;
    }
}
