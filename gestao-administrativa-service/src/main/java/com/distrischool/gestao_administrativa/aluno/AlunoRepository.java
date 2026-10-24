package com.distrischool.gestao_administrativa.aluno;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AlunoRepository extends JpaRepository<Aluno, Long>, JpaSpecificationExecutor<Aluno> {

    @Query(value = "SELECT nextval('aluno_matricula_seq')", nativeQuery = true)
    long nextMatriculaSequence();
}
