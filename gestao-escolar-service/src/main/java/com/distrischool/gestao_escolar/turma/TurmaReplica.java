package com.distrischool.gestao_escolar.turma;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cópia local de uma turma do gestao-administrativa-service. Somente o consumidor do
 * {@code schedule.updated} e o fallback síncrono escrevem aqui; a linha nunca é apagada.
 */
@Entity
@Table(name = "turma_replica")
@Getter
@Setter
@NoArgsConstructor
public class TurmaReplica implements Persistable<Long> {

    /** O {@code turmaId} do outro serviço: atribuído, não gerado. */
    @Id
    private Long id;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "ano_letivo", nullable = false)
    private Integer anoLetivo;

    @Column(nullable = false, length = 20)
    private String turno;

    private Integer capacidade;

    @Column(nullable = false)
    private boolean removida;

    /** {@code occurredAt} do último evento aplicado; {@code null} se a linha veio do fallback. */
    @Column(name = "ultimo_evento_em")
    private Instant ultimoEventoEm;

    @OneToMany(mappedBy = "turma", cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    private List<TurmaDisciplinaReplica> disciplinas = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Com id atribuído, o Spring Data não sabe sozinho se deve inserir ou atualizar. */
    @Transient
    private boolean nova = true;

    public TurmaReplica(Long id) {
        this.id = id;
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

    public Optional<TurmaDisciplinaReplica> disciplina(Long disciplinaId) {
        return disciplinas.stream()
                .filter(d -> d.getId().getDisciplinaId().equals(disciplinaId))
                .findFirst();
    }

    /** A disciplina está ofertada hoje na turma (existe na réplica e não foi retirada). */
    public boolean ofertaDisciplina(Long disciplinaId) {
        return disciplina(disciplinaId).filter(d -> !d.isRemovida()).isPresent();
    }
}
