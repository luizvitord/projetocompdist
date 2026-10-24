package com.distrischool.gestao_escolar.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** {@code ausencias} traz só FALTA e ATRASO, por aluno (os consumidores não conhecem matrículas). */
public record AttendanceRecordedEvent(
        Long chamadaId,
        Long turmaId,
        Long disciplinaId,
        LocalDate data,
        List<Ausencia> ausencias,
        Instant occurredAt
) {

    public record Ausencia(Long alunoId, String status) {
    }
}
