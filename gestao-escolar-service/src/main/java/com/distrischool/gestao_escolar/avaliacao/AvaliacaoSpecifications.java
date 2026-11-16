package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.common.BuscaTexto;
import com.distrischool.gestao_escolar.turma.TurmaDisciplinaReplica;
import com.distrischool.gestao_escolar.turma.TurmaReplica;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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

    /**
     * Descrição da avaliação, código ou nome da turma, ou nome da disciplina contêm o texto. Turma e
     * disciplina vêm da réplica local (a disciplina pelo nome da oferta naquela turma).
     */
    static Specification<Avaliacao> busca(String busca) {
        return (root, query, cb) -> {
            Subquery<Long> turmas = query.subquery(Long.class);
            Root<TurmaReplica> turma = turmas.from(TurmaReplica.class);
            turmas.select(turma.get("id"))
                    .where(BuscaTexto.algumContem(cb, busca, turma.get("codigo"), turma.get("nome")));

            Subquery<Integer> disciplinas = query.subquery(Integer.class);
            Root<TurmaDisciplinaReplica> oferta = disciplinas.from(TurmaDisciplinaReplica.class);
            disciplinas.select(cb.literal(1)).where(
                    cb.equal(oferta.get("id").get("turmaId"), root.get("turmaId")),
                    cb.equal(oferta.get("id").get("disciplinaId"), root.get("disciplinaId")),
                    BuscaTexto.algumContem(cb, busca, oferta.get("nome")));

            return cb.or(
                    BuscaTexto.algumContem(cb, busca, root.get("descricao")),
                    root.get("turmaId").in(turmas),
                    cb.exists(disciplinas));
        };
    }
}
