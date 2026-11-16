package com.distrischool.gestao_administrativa.disciplina;

import com.distrischool.gestao_administrativa.common.BuscaTexto;
import org.springframework.data.jpa.domain.Specification;

final class DisciplinaSpecifications {

    private DisciplinaSpecifications() {
    }

    static Specification<Disciplina> nomeContem(String nome) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("nome")), "%" + nome.toLowerCase() + "%");
    }

    /** Código ou nome contêm o texto (sem diferenciar maiúsculas nem acentos). */
    static Specification<Disciplina> busca(String busca) {
        return (root, query, cb) -> BuscaTexto.algumContem(cb, busca, root.get("codigo"), root.get("nome"));
    }

    static Specification<Disciplina> ativaIgual(boolean ativa) {
        return (root, query, cb) -> cb.equal(root.get("ativa"), ativa);
    }
}
