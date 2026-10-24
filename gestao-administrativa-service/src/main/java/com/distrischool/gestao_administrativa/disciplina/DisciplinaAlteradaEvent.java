package com.distrischool.gestao_administrativa.disciplina;

/**
 * Evento interno (não vai para o Kafka): o nome ou o código de uma disciplina mudou. As turmas que
 * a ofertam reagem a ele, na mesma transação, republicando o seu {@code schedule.updated}.
 */
public record DisciplinaAlteradaEvent(Long disciplinaId) {
}
