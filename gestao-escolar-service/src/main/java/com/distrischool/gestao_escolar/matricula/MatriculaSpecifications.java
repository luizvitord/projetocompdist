package com.distrischool.gestao_escolar.matricula;

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
}
