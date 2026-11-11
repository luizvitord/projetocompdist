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

/** Nome de uma disciplina do gestao-administrativa-service, para montar o texto das notificações. */
@Entity
@Table(name = "disciplina_nome")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisciplinaNome implements Persistable<Long> {

    /** O {@code disciplinaId} do outro serviço: atribuído, não gerado. */
    @Id
    @Column(name = "disciplina_id")
    private Long id;

    @Column(nullable = false)
    private String nome;

    /** {@code occurredAt} do último {@code schedule.updated} que trouxe esta disciplina. */
    @Column(name = "ultimo_evento_em", nullable = false)
    private Instant ultimoEventoEm;

    /** Com id atribuído, o Spring Data não sabe sozinho se deve inserir ou atualizar. */
    @Transient
    private boolean nova = true;

    public DisciplinaNome(Long id) {
        this.id = id;
    }

    void atualizar(String nome, Instant ultimoEventoEm) {
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
