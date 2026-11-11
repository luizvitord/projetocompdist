package com.distrischool.notificacao.nomes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/** Nome de uma turma do gestao-administrativa-service, para montar o texto das notificações. */
@Entity
@Table(name = "turma_nome")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TurmaNome implements Persistable<Long> {

    /** O {@code turmaId} do outro serviço: atribuído, não gerado. */
    @Id
    @Column(name = "turma_id")
    private Long id;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    /** {@code occurredAt} do último {@code schedule.updated} aplicado. */
    @Column(name = "ultimo_evento_em", nullable = false)
    private Instant ultimoEventoEm;

    /** Com id atribuído, o Spring Data não sabe sozinho se deve inserir ou atualizar. */
    @Transient
    private boolean nova = true;

    public TurmaNome(Long id) {
        this.id = id;
    }

    void atualizar(String codigo, String nome, Instant ultimoEventoEm) {
        this.codigo = codigo;
        this.nome = nome;
        this.ultimoEventoEm = ultimoEventoEm;
    }

    @Override
    public boolean isNew() {
        return nova;
    }

    @PostLoad
    @PostPersist
    void marcarComoExistente() {
        this.nova = false;
    }
}
