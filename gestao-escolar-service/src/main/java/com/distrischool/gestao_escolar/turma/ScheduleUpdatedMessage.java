package com.distrischool.gestao_escolar.turma;

import java.time.Instant;
import java.util.List;

/**
 * Leitura local do evento {@code schedule.updated} do gestao-administrativa-service. Não depende
 * das classes do produtor: só os campos do contrato; campos desconhecidos são ignorados.
 * {@code disciplinas} é sempre a lista completa de ofertas da turma, não um delta.
 */
public record ScheduleUpdatedMessage(
        Long turmaId,
        String codigo,
        String nome,
        Integer anoLetivo,
        String turno,
        Integer capacidade,
        List<DisciplinaOfertada> disciplinas,
        Acao acao,
        Instant occurredAt
) {

    public record DisciplinaOfertada(Long disciplinaId, String codigo, String nome, Long professorId) {
    }

    public enum Acao {
        CRIADA, ATUALIZADA, REMOVIDA
    }
}
