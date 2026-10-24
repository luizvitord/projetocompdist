package com.distrischool.gestao_administrativa.turma;

import org.springframework.data.jpa.domain.Specification;

final class TurmaSpecifications {

    private TurmaSpecifications() {
    }

    static Specification<Turma> anoLetivoIgual(Integer anoLetivo) {
        return (root, query, cb) -> cb.equal(root.get("anoLetivo"), anoLetivo);
    }

    static Specification<Turma> turnoIgual(Turno turno) {
        return (root, query, cb) -> cb.equal(root.get("turno"), turno);
    }

    static Specification<Turma> possuiProfessor(Long professorId) {
        return (root, query, cb) -> cb.equal(root.join("professores").get("id"), professorId);
    }
}
