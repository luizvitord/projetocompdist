package com.distrischool.gestao_administrativa.disciplina;

import org.springframework.data.jpa.domain.Specification;

final class DisciplinaSpecifications {

    private DisciplinaSpecifications() {
    }

    static Specification<Disciplina> nomeContem(String nome) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("nome")), "%" + nome.toLowerCase() + "%");
    }

    static Specification<Disciplina> ativaIgual(boolean ativa) {
        return (root, query, cb) -> cb.equal(root.get("ativa"), ativa);
    }
}
