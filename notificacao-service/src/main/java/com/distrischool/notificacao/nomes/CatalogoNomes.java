package com.distrischool.notificacao.nomes;

import java.util.Optional;

/**
 * Nomes de turmas e disciplinas conhecidos localmente. Vazio quando o {@code schedule.updated}
 * correspondente ainda não chegou; quem monta o texto usa então um nome neutro ("turma #12").
 */
public interface CatalogoNomes {

    Optional<String> nomeTurma(Long turmaId);

    Optional<String> nomeDisciplina(Long disciplinaId);
}
