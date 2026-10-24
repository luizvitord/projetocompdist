package com.distrischool.gestao_escolar.turma;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TurmaReplicaRepository extends JpaRepository<TurmaReplica, Long> {

    /**
     * Lê a turma com bloqueio pessimista ({@code SELECT ... FOR UPDATE}): serializa a aplicação
     * de eventos e as matrículas concorrentes na mesma turma.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TurmaReplica t where t.id = :id")
    Optional<TurmaReplica> findByIdParaAtualizacao(@Param("id") Long id);
}
