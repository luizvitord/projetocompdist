package com.distrischool.gestao_escolar.matricula;

import com.distrischool.gestao_escolar.aluno.Aluno;
import com.distrischool.gestao_escolar.common.BuscaTexto;
import com.distrischool.gestao_escolar.turma.TurmaReplica;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

final class MatriculaSpecifications {

    private MatriculaSpecifications() {
    }

    static Specification<Matricula> alunoIgual(Long alunoId) {
        return (root, query, cb) -> cb.equal(root.get("alunoId"), alunoId);
    }

    static Specification<Matricula> turmaIgual(Long turmaId) {
        return (root, query, cb) -> cb.equal(root.get("turmaId"), turmaId);
    }

    static Specification<Matricula> anoLetivoIgual(Integer anoLetivo) {
        return (root, query, cb) -> cb.equal(root.get("anoLetivo"), anoLetivo);
    }

    static Specification<Matricula> statusIgual(StatusMatricula status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Nome ou número de matrícula do aluno, ou código ou nome da turma (réplica local), contêm o
     * texto. Subconsultas nas tabelas do próprio serviço; nada é buscado em outro serviço.
     */
    static Specification<Matricula> busca(String busca) {
        return (root, query, cb) -> {
            Subquery<Long> alunos = query.subquery(Long.class);
            Root<Aluno> aluno = alunos.from(Aluno.class);
            alunos.select(aluno.get("id"))
                    .where(BuscaTexto.algumContem(cb, busca, aluno.get("nome"), aluno.get("matricula")));

            Subquery<Long> turmas = query.subquery(Long.class);
            Root<TurmaReplica> turma = turmas.from(TurmaReplica.class);
            turmas.select(turma.get("id"))
                    .where(BuscaTexto.algumContem(cb, busca, turma.get("codigo"), turma.get("nome")));

            return cb.or(root.get("alunoId").in(alunos), root.get("turmaId").in(turmas));
        };
    }
}
