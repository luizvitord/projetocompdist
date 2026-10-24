package com.distrischool.gestao_escolar.avaliacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long>, JpaSpecificationExecutor<Avaliacao> {

    boolean existsByTurmaIdAndDisciplinaIdAndBimestreAndTipo(Long turmaId, Long disciplinaId, Integer bimestre,
                                                              TipoAvaliacao tipo);

    boolean existsByTurmaIdAndDisciplinaIdAndBimestreAndTipoAndIdNot(Long turmaId, Long disciplinaId, Integer bimestre,
                                                                      TipoAvaliacao tipo, Long id);

    List<Avaliacao> findByTurmaIdIn(Collection<Long> turmaIds);
}
