package com.distrischool.gestao_administrativa.turma;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TurmaRepository extends JpaRepository<Turma, Long>, JpaSpecificationExecutor<Turma> {

    @Query("select distinct t from Turma t join t.disciplinas td where td.disciplina.id = :disciplinaId order by t.id")
    List<Turma> findByDisciplinaId(@Param("disciplinaId") Long disciplinaId);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);
}
