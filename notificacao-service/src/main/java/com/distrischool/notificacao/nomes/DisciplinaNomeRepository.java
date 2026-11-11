package com.distrischool.notificacao.nomes;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DisciplinaNomeRepository extends JpaRepository<DisciplinaNome, Long> {

    /**
     * {@code SELECT ... FOR UPDATE}: a mesma disciplina chega em eventos de turmas diferentes, que
     * podem ser processados ao mesmo tempo por réplicas diferentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DisciplinaNome d where d.id = :id")
    Optional<DisciplinaNome> findByIdParaAtualizacao(@Param("id") Long id);
}
