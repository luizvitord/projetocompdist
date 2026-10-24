package com.distrischool.gestao_escolar.avaliacao;

import org.springframework.data.jpa.domain.Specification;

final class AvaliacaoSpecifications {

    private AvaliacaoSpecifications() {
    }

    static Specification<Avaliacao> turmaIgual(Long turmaId) {
        return (root, query, cb) -> cb.equal(root.get("turmaId"), turmaId);
    }

    static Specification<Avaliacao> disciplinaIgual(Long disciplinaId) {
        return (root, query, cb) -> cb.equal(root.get("disciplinaId"), disciplinaId);
    }

    static Specification<Avaliacao> bimestreIgual(Integer bimestre) {
        return (root, query, cb) -> cb.equal(root.get("bimestre"), bimestre);
    }
}
