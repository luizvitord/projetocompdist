package com.distrischool.gestao_escolar.turma;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Cópia local da oferta de uma disciplina em uma turma; a linha nunca é apagada. */
@Entity
@Table(name = "turma_disciplina_replica")
@Getter
@Setter
@NoArgsConstructor
public class TurmaDisciplinaReplica {

    @EmbeddedId
    private TurmaDisciplinaReplicaId id;

    @MapsId("turmaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turma_id")
    private TurmaReplica turma;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "professor_id")
    private Long professorId;

    @Column(nullable = false)
    private boolean removida;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TurmaDisciplinaReplica(TurmaReplica turma, Long disciplinaId) {
        this.id = new TurmaDisciplinaReplicaId(turma.getId(), disciplinaId);
        this.turma = turma;
    }
}
