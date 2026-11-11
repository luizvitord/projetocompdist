package com.distrischool.notificacao.nomes;

import java.time.Instant;
import java.util.List;

/**
 * Leitura local do evento {@code schedule.updated} do gestao-administrativa-service: só os campos
 * usados no cache de nomes. Os demais campos do contrato (ano letivo, turno, capacidade, ação...)
 * são ignorados, assim como campos novos.
 */
public record ScheduleUpdatedMessage(
        Long turmaId,
        String codigo,
        String nome,
        List<DisciplinaOfertada> disciplinas,
        Instant occurredAt
) {

    public record DisciplinaOfertada(Long disciplinaId, String nome) {
    }
}
