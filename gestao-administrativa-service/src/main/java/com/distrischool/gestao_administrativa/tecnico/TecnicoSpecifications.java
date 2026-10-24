package com.distrischool.gestao_administrativa.tecnico;

import org.springframework.data.jpa.domain.Specification;

final class TecnicoSpecifications {

    private TecnicoSpecifications() {
    }

    static Specification<Tecnico> nomeContem(String nome) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("nome")), "%" + nome.toLowerCase() + "%");
    }

    static Specification<Tecnico> cargoIgual(Cargo cargo) {
        return (root, query, cb) -> cb.equal(root.get("cargo"), cargo);
    }

    static Specification<Tecnico> setorIgual(String setor) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("setor")), setor.toLowerCase());
    }

    static Specification<Tecnico> ativoIgual(boolean ativo) {
        return (root, query, cb) -> cb.equal(root.get("ativo"), ativo);
    }
}
