package com.distrischool.gestao_escolar.frequencia;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Registro de presença de uma turma em uma disciplina em uma data. */
@Entity
@Table(name = "chamada")
@Getter
@Setter
@NoArgsConstructor
public class Chamada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turma_id", nullable = false)
    private Long turmaId;

    @Column(name = "disciplina_id", nullable = false)
    private Long disciplinaId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "professor_id")
    private Long professorId;

    @OneToMany(mappedBy = "chamada", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<RegistroFrequencia> registros = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
