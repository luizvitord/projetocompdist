package com.distrischool.gestao_administrativa.event;

import java.time.Instant;

public record StudentCreatedEvent(
        Long alunoId,
        String matricula,
        String nome,
        String email,
        Instant occurredAt
) {
}
