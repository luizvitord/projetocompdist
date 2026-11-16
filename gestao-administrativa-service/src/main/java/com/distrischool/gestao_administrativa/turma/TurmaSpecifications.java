package com.distrischool.gestao_administrativa.turma;

import com.distrischool.gestao_administrativa.common.BuscaTexto;
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

    /** Código ou nome contêm o texto (sem diferenciar maiúsculas nem acentos). */
    static Specification<Turma> busca(String busca) {
        return (root, query, cb) -> BuscaTexto.algumContem(cb, busca, root.get("codigo"), root.get("nome"));
    }
}
