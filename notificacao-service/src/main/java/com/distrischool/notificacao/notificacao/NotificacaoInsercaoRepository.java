package com.distrischool.notificacao.notificacao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Inserção idempotente de notificações, via JDBC: o JPA não tem {@code ON CONFLICT DO NOTHING}, e
 * uma violação do índice único marcaria a transação inteira para rollback.
 * <p>
 * O índice único é (evento_hash, destinatario_perfil, destinatario_referencia_id), criado com
 * {@code NULLS NOT DISTINCT}: sem isso, duas linhas de ADMIN (referência nula) nunca entrariam em
 * conflito. O {@link JdbcTemplate} participa da transação em andamento.
 */
@Repository
@RequiredArgsConstructor
public class NotificacaoInsercaoRepository {

    private final JdbcTemplate jdbc;

    /**
     * Grava a notificação, a menos que o mesmo evento já tenha gerado uma para o mesmo destinatário
     * (reentrega do Kafka).
     *
     * @return a notificação gravada, ou vazio se ela já existia
     */
    public Optional<NotificacaoCriada> inserirSeNova(String eventoHash, NovaNotificacao nova, Instant criadaEm) {
        Destinatario destinatario = nova.destinatario();
        Long id = jdbc.query("""
                        INSERT INTO notificacao (destinatario_perfil, destinatario_referencia_id, tipo, titulo,
                                                 mensagem, link, criada_em, evento_hash)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (evento_hash, destinatario_perfil, destinatario_referencia_id) DO NOTHING
                        RETURNING id
                        """,
                rs -> rs.next() ? rs.getLong(1) : null,
                destinatario.perfil().name(), destinatario.referenciaId(), nova.tipo().name(), nova.titulo(),
                nova.mensagem(), nova.link(), OffsetDateTime.ofInstant(criadaEm, ZoneOffset.UTC), eventoHash);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(new NotificacaoCriada(id, destinatario, nova.tipo(), nova.titulo(), nova.mensagem(),
                nova.link(), criadaEm));
    }
}
