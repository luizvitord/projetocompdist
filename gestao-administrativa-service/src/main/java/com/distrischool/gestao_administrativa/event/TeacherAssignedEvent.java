package com.distrischool.gestao_administrativa.event;

import java.time.Instant;

public record TeacherAssignedEvent(
        Long professorId,
        String professorNome,
        String professorEmail,
        Long turmaId,
        String turmaCodigo,
        String turmaNome,
        Instant occurredAt
) {
}
