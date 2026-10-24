package com.distrischool.gestao_escolar.event;

import java.time.Instant;

/** {@code turmaDestinoId} só é preenchido em transferências entre turmas. */
public record EnrollmentUpdatedEvent(
        Long matriculaId,
        Long alunoId,
        Long turmaId,
        String statusAnterior,
        String statusNovo,
        Long turmaDestinoId,
        Instant occurredAt
) {
}
