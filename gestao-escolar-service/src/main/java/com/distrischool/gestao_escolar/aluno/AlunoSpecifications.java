package com.distrischool.gestao_escolar.aluno;

import com.distrischool.gestao_escolar.matricula.Matricula;
import com.distrischool.gestao_escolar.matricula.StatusMatricula;
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

    /** Alunos com matrícula {@code ATIVA} na turma. */
    static Specification<Aluno> matriculadoNaTurma(Long turmaId) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Matricula> matricula = sub.from(Matricula.class);
            sub.select(matricula.get("alunoId"))
                    .where(cb.equal(matricula.get("turmaId"), turmaId),
                            cb.equal(matricula.get("status"), StatusMatricula.ATIVA));
            return root.get("id").in(sub);
        };
    }
}
