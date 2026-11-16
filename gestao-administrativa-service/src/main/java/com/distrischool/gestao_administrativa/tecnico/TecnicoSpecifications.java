package com.distrischool.gestao_administrativa.tecnico;

import com.distrischool.gestao_administrativa.common.BuscaTexto;
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

    /** Nome, email ou setor contêm o texto. O CPF nunca entra na busca (LGPD). */
    static Specification<Tecnico> busca(String busca) {
        return (root, query, cb) -> BuscaTexto.algumContem(cb, busca,
                root.get("nome"), root.get("email"), root.get("setor"));
    }

    static Specification<Tecnico> ativoIgual(boolean ativo) {
        return (root, query, cb) -> cb.equal(root.get("ativo"), ativo);
    }
}
