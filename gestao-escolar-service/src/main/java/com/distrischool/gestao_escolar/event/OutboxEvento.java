package com.distrischool.gestao_escolar.event;

/** Linha pendente da outbox; {@code payload} é o JSON final da mensagem, como foi serializado. */
public record OutboxEvento(
        long id,
        String topico,
        String chave,
        String payload,
        int tentativas
) {
}
