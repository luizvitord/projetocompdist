package com.distrischool.gestao_escolar.turma;

import java.util.List;

/**
 * Resposta de {@code GET /api/turmas/{id}} do gestao-administrativa-service, só com os campos
 * usados aqui (os demais, como {@code professores} e {@code professorNome}, são ignorados).
 */
public record TurmaAdministrativaResponse(
        Long id,
        String codigo,
        String nome,
        Integer anoLetivo,
        String turno,
        Integer capacidade,
        List<ScheduleUpdatedMessage.DisciplinaOfertada> disciplinas
) {
}
