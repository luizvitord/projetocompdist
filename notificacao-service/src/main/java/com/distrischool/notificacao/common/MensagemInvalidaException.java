package com.distrischool.notificacao.common;

/**
 * Evento recebido fora do contrato (JSON inválido ou campo obrigatório ausente). Repetir não
 * resolve, então o binder não faz novas tentativas: o registro vai direto para a DLT.
 * <p>
 * A mensagem traz só o tópico e o nome do campo, nunca o payload (dados de alunos).
 */
public class MensagemInvalidaException extends RuntimeException {

    public MensagemInvalidaException(String message) {
        super(message);
    }
}
