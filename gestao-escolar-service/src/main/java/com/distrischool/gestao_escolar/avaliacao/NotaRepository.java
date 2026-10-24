package com.distrischool.gestao_escolar.avaliacao;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotaRepository extends JpaRepository<Nota, Long> {

    /** Serializa correções concorrentes da mesma nota (cada uma grava um histórico). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Nota n join fetch n.avaliacao where n.id = :id")
    Optional<Nota> findByIdParaAtualizacao(@Param("id") Long id);

    boolean existsByAvaliacaoId(Long avaliacaoId);

    List<Nota> findByAvaliacaoIdOrderByMatriculaId(Long avaliacaoId);

    List<Nota> findByAvaliacaoIdAndMatriculaIdIn(Long avaliacaoId, Collection<Long> matriculaIds);

    List<Nota> findByMatriculaIdIn(Collection<Long> matriculaIds);
}
