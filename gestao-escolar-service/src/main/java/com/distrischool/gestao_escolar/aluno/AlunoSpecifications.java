package com.distrischool.gestao_escolar.aluno;

import org.springframework.data.jpa.domain.Specification;

final class AlunoSpecifications {

    private AlunoSpecifications() {
    }

    static Specification<Aluno> nomeContem(String nome) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("nome")), "%" + nome.toLowerCase() + "%");
    }

    static Specification<Aluno> matriculaIgual(String matricula) {
        return (root, query, cb) -> cb.equal(root.get("matricula"), matricula);
    }
}
