package com.distrischool.gestao_administrativa.aluno;

import com.distrischool.gestao_administrativa.turma.Turma;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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

    static Specification<Aluno> pertenceATurma(Long turmaId) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Turma> turma = sub.from(Turma.class);
            Join<Turma, Aluno> alunos = turma.join("alunos");
            sub.select(alunos.get("id"))
                    .where(cb.equal(turma.get("id"), turmaId));
            return root.get("id").in(sub);
        };
    }
}
