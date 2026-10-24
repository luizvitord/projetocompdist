package com.distrischool.gestao_escolar.avaliacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma correção de nota. Somente inserção: {@link Immutable} impede updates pelo Hibernate e um
 * trigger no banco recusa UPDATE e DELETE.
 */
@Entity
@Immutable
@Table(name = "nota_historico")
@Getter
@Setter
@NoArgsConstructor
public class NotaHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nota_id", nullable = false, updatable = false)
    private Long notaId;

    @Column(name = "valor_anterior", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal valorAnterior;

    @Column(name = "valor_novo", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal valorNovo;

    @Column(nullable = false, updatable = false, length = 500)
    private String motivo;

    @CreationTimestamp
    @Column(name = "alterado_em", nullable = false, updatable = false)
    private Instant alteradoEm;
}
