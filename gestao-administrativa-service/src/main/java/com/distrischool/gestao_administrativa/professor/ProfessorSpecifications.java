package com.distrischool.gestao_administrativa.professor;

import com.distrischool.gestao_administrativa.common.BuscaTexto;
import org.springframework.data.jpa.domain.Specification;

final class ProfessorSpecifications {

    private ProfessorSpecifications() {
    }

    static Specification<Professor> nomeContem(String nome) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("nome")), "%" + nome.toLowerCase() + "%");
    }

    /** Nome, email ou qualificação contêm o texto (sem diferenciar maiúsculas nem acentos). */
    static Specification<Professor> busca(String busca) {
        return (root, query, cb) -> BuscaTexto.algumContem(cb, busca,
                root.get("nome"), root.get("email"), root.get("qualificacao"));
    }
}
