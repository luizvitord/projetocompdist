package com.distrischool.gestao_escolar.frequencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;

public interface ChamadaRepository extends JpaRepository<Chamada, Long>, JpaSpecificationExecutor<Chamada> {

    boolean existsByTurmaIdAndDisciplinaIdAndData(Long turmaId, Long disciplinaId, LocalDate data);

    boolean existsByTurmaIdAndDisciplinaIdAndDataAndIdNot(Long turmaId, Long disciplinaId, LocalDate data, Long id);
}
