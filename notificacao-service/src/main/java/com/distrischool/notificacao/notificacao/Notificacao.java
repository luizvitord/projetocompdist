package com.distrischool.notificacao.notificacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Notificação de um destinatário. As linhas são inseridas por {@link NotificacaoInsercaoRepository}
 * (JDBC, com {@code ON CONFLICT DO NOTHING}); por aqui só se lê e se marca como lida.
 */
@Entity
@Table(name = "notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "destinatario_perfil", nullable = false, updatable = false, length = 20)
    private Perfil destinatarioPerfil;

    /** {@code null} para ADMIN. */
    @Column(name = "destinatario_referencia_id", updatable = false)
    private Long destinatarioReferenciaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TipoNotificacao tipo;

    @Column(nullable = false, updatable = false, length = 100)
    private String titulo;

    @Column(nullable = false, updatable = false, length = 500)
    private String mensagem;

    /** Rota do frontend relacionada à notificação (ex.: {@code /alunos/12}). */
    @Column(updatable = false, length = 200)
    private String link;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    /** {@code null} = não lida. */
    @Column(name = "lida_em")
    private Instant lidaEm;

    /** SHA-256 do evento de origem; com o destinatário, impede notificações duplicadas. */
    @Column(name = "evento_hash", nullable = false, updatable = false, length = 64)
    private String eventoHash;

    Notificacao(Long id, Destinatario destinatario, TipoNotificacao tipo, String titulo, String mensagem,
                String link, Instant criadaEm, String eventoHash) {
        this.id = id;
        this.destinatarioPerfil = destinatario.perfil();
        this.destinatarioReferenciaId = destinatario.referenciaId();
        this.tipo = tipo;
        this.titulo = titulo;
        this.mensagem = mensagem;
        this.link = link;
        this.criadaEm = criadaEm;
        this.eventoHash = eventoHash;
    }

    public boolean isLida() {
        return lidaEm != null;
    }

    /** Marcar de novo não muda a data da primeira leitura. */
    void marcarComoLida(Instant quando) {
        if (lidaEm == null) {
            lidaEm = quando;
        }
    }
}
