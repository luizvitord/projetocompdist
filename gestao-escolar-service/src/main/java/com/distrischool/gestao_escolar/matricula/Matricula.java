package com.distrischool.gestao_escolar.matricula;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/** Vínculo de um aluno com uma turma em um ano letivo. Nunca é apagada. */
@Entity
@Table(name = "matricula")
@Getter
@Setter
@NoArgsConstructor
public class Matricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aluno_id", nullable = false, updatable = false)
    private Long alunoId;

    @Column(name = "turma_id", nullable = false, updatable = false)
    private Long turmaId;

    @Column(name = "ano_letivo", nullable = false, updatable = false)
    private Integer anoLetivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusMatricula status;

    @Column(name = "data_matricula", nullable = false, updatable = false)
    private LocalDate dataMatricula;

    @Column(name = "data_encerramento")
    private LocalDate dataEncerramento;

    @Column(length = 500)
    private String motivo;

    @Column(name = "matricula_origem_id", updatable = false)
    private Long matriculaOrigemId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
