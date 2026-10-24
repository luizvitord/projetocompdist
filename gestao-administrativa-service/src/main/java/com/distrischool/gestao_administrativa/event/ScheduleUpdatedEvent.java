package com.distrischool.gestao_administrativa.event;

import java.time.Instant;
import java.util.List;

/**
 * Estado completo de uma turma, publicado em {@code schedule.updated} sempre que ela ou suas
 * ofertas de disciplina mudam. {@code disciplinas} é a lista atual inteira, não um delta; em
 * {@link Acao#REMOVIDA} os campos trazem o último estado da turma.
 * <p>
 * Este formato é um contrato com o gestao-escolar-service: campos podem ser acrescentados,
 * mas não removidos nem renomeados.
 */
public record ScheduleUpdatedEvent(
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
