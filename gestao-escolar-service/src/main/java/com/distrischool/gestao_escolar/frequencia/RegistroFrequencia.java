package com.distrischool.gestao_escolar.frequencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Presença de uma matrícula em uma chamada. */
@Entity
@Table(name = "registro_frequencia")
@Getter
@Setter
@NoArgsConstructor
public class RegistroFrequencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chamada_id", nullable = false, updatable = false)
    private Chamada chamada;

    @Column(name = "matricula_id", nullable = false, updatable = false)
    private Long matriculaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusPresenca status;

    @Column(length = 255)
    private String observacao;
}
