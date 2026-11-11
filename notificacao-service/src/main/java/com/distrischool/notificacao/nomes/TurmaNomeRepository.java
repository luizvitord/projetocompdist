package com.distrischool.notificacao.nomes;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TurmaNomeRepository extends JpaRepository<TurmaNome, Long> {

    /** {@code SELECT ... FOR UPDATE}: serializa a aplicação de eventos da mesma turma. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TurmaNome t where t.id = :id")
    Optional<TurmaNome> findByIdParaAtualizacao(@Param("id") Long id);
}
