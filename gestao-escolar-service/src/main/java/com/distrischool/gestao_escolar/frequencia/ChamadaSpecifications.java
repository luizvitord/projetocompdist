package com.distrischool.gestao_escolar.frequencia;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

final class ChamadaSpecifications {

    private ChamadaSpecifications() {
    }

    static Specification<Chamada> turmaIgual(Long turmaId) {
        return (root, query, cb) -> cb.equal(root.get("turmaId"), turmaId);
    }

    static Specification<Chamada> disciplinaIgual(Long disciplinaId) {
        return (root, query, cb) -> cb.equal(root.get("disciplinaId"), disciplinaId);
    }

    static Specification<Chamada> dataAPartirDe(LocalDate inicio) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("data"), inicio);
    }

    static Specification<Chamada> dataAte(LocalDate fim) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("data"), fim);
    }
}
