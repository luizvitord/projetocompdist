package com.distrischool.gestao_escolar.avaliacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotaHistoricoRepository extends JpaRepository<NotaHistorico, Long> {

    List<NotaHistorico> findByNotaIdOrderByAlteradoEmAscIdAsc(Long notaId);
}
