package com.distrischool.notificacao.notificacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Leitura e marcação de leitura. Toda consulta filtra pelo destinatário (ver
 * {@link NotificacaoSpecifications#doDestinatario}); a referência nula (ADMIN) precisa de
 * {@code IS NULL}, por isso as atualizações em lote têm duas variantes.
 */
public interface NotificacaoRepository extends JpaRepository<Notificacao, Long>, JpaSpecificationExecutor<Notificacao> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notificacao n set n.lidaEm = :quando
             where n.lidaEm is null
               and n.destinatarioPerfil = :perfil
               and n.destinatarioReferenciaId = :referenciaId
            """)
    int marcarTodasComoLidas(@Param("perfil") Perfil perfil, @Param("referenciaId") Long referenciaId,
                             @Param("quando") Instant quando);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notificacao n set n.lidaEm = :quando
             where n.lidaEm is null
               and n.destinatarioPerfil = :perfil
               and n.destinatarioReferenciaId is null
            """)
    int marcarTodasComoLidasSemReferencia(@Param("perfil") Perfil perfil, @Param("quando") Instant quando);
}
