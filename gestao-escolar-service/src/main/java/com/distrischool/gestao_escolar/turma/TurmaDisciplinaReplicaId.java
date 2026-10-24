package com.distrischool.gestao_escolar.turma;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TurmaDisciplinaReplicaId implements Serializable {

    @Column(name = "turma_id")
    private Long turmaId;

    @Column(name = "disciplina_id")
    private Long disciplinaId;
}
