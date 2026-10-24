package com.distrischool.gestao_administrativa.event;

import java.time.Instant;

public record ScheduleUpdatedEvent(
        Long turmaId,
        String turmaCodigo,
        Acao acao,
        Instant occurredAt
) {

    public enum Acao {
        CRIADA, ATUALIZADA, REMOVIDA
    }
}
