package com.distrischool.gestao_escolar.event;

import java.time.Instant;

public record StudentCreatedEvent(
        Long alunoId,
        String matricula,
        String nome,
        String email,
        Instant occurredAt
) {
}
