package com.distrischool.gestao_escolar.matricula;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatriculaRepository extends JpaRepository<Matricula, Long>, JpaSpecificationExecutor<Matricula> {

    /** Serializa mudanças de status concorrentes na mesma matrícula. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Matricula m where m.id = :id")
    Optional<Matricula> findByIdParaAtualizacao(@Param("id") Long id);

    boolean existsByAlunoId(Long alunoId);

    boolean existsByAlunoIdAndAnoLetivoAndStatusIn(Long alunoId, Integer anoLetivo, Collection<StatusMatricula> status);

    long countByTurmaIdAndStatus(Long turmaId, StatusMatricula status);

    List<Matricula> findByTurmaIdAndStatus(Long turmaId, StatusMatricula status);

    List<Matricula> findByAlunoIdAndAnoLetivoOrderByIdAsc(Long alunoId, Integer anoLetivo);
}
