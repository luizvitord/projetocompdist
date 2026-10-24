package com.distrischool.gestao_escolar.event;

import java.time.Instant;

public record EnrollmentCreatedEvent(
        Long matriculaId,
        Long alunoId,
        Long turmaId,
        Integer anoLetivo,
        Instant occurredAt
) {
}
