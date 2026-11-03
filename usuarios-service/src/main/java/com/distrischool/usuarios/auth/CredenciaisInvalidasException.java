package com.distrischool.usuarios.auth;

/**
 * Mesma mensagem para email inexistente, senha errada ou usuário desativado: a resposta não
 * revela quais emails estão cadastrados.
 */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Email ou senha inválidos");
    }
}
