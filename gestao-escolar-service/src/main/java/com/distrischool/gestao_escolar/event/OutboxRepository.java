package com.distrischool.gestao_escolar.event;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Acesso à tabela {@code outbox_evento} via JDBC: o payload é gravado como {@code json} sem passar
 * pelo mapeamento do JPA, e a busca dos pendentes precisa de {@code FOR UPDATE SKIP LOCKED}.
 * O {@link JdbcTemplate} participa da transação JPA em andamento.
 */
@Repository
@RequiredArgsConstructor
public class OutboxRepository {

    private final JdbcTemplate jdbc;

    public void inserir(String topico, String chave, String payload) {
        jdbc.update("INSERT INTO outbox_evento (topico, chave, payload) VALUES (?, ?, CAST(? AS json))",
                topico, chave, payload);
    }

    /**
     * Bloqueia até {@code limite} pendentes, em ordem de gravação, até o fim da transação. Linhas já
     * bloqueadas por outra réplica são puladas, então cada réplica publica um lote diferente.
     */
    public List<OutboxEvento> bloquearPendentes(int limite) {
        return jdbc.query("""
                        SELECT id, topico, chave, payload::text AS payload, tentativas
                          FROM outbox_evento
                         WHERE publicado_em IS NULL
                         ORDER BY id
                         LIMIT ?
                           FOR UPDATE SKIP LOCKED
                        """,
                (rs, linha) -> new OutboxEvento(rs.getLong("id"), rs.getString("topico"), rs.getString("chave"),
                        rs.getString("payload"), rs.getInt("tentativas")),
                limite);
    }

    // clock_timestamp(): o momento da confirmação, não o início da transação do lote
    public void marcarPublicado(long id) {
        jdbc.update("UPDATE outbox_evento SET publicado_em = clock_timestamp() WHERE id = ?", id);
    }

    public void registrarFalha(long id, String erro) {
        jdbc.update("UPDATE outbox_evento SET tentativas = tentativas + 1, ultimo_erro = ? WHERE id = ?", erro, id);
    }

    public long contarPendentes() {
        Long total = jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE publicado_em IS NULL", Long.class);
        return total != null ? total : 0;
    }

    public int apagarPublicadosAntesDe(Instant limite) {
        return jdbc.update("DELETE FROM outbox_evento WHERE publicado_em < ?",
                OffsetDateTime.ofInstant(limite, ZoneOffset.UTC));
    }
}
