package com.distrischool.gestao_administrativa.turma;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

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

    /** Parte do código ou do nome, sem diferenciar maiúsculas; % e _ digitados são literais. */
    static Specification<Turma> codigoOuNomeContem(String busca) {
        String padrao = "%" + escaparLike(busca.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("codigo")), padrao, '\\'),
                cb.like(cb.lower(root.get("nome")), padrao, '\\'));
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
