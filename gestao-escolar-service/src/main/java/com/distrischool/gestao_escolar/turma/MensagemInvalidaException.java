package com.distrischool.gestao_escolar.turma;

/**
 * Evento recebido fora do contrato (campo obrigatório ausente). Repetir não resolve, então o
 * error handler do Kafka não faz novas tentativas: o registro vai direto para a DLT.
 */
public class MensagemInvalidaException extends RuntimeException {

    public MensagemInvalidaException(String message) {
        super(message);
    }
}
