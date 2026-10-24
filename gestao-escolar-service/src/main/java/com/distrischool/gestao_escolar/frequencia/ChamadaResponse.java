package com.distrischool.gestao_escolar.frequencia;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record ChamadaResponse(
        Long id,
        Long turmaId,
        Long disciplinaId,
        LocalDate data,
        Long professorId,
        List<Registro> registros,
        Instant createdAt,
        Instant updatedAt
) {

    public record Registro(Long matriculaId, StatusPresenca status, String observacao) {
    }

    public static ChamadaResponse from(Chamada chamada) {
        List<Registro> registros = chamada.getRegistros().stream()
                .map(r -> new Registro(r.getMatriculaId(), r.getStatus(), r.getObservacao()))
                .sorted(Comparator.comparing(Registro::matriculaId))
                .toList();
        return new ChamadaResponse(
                chamada.getId(),
                chamada.getTurmaId(),
                chamada.getDisciplinaId(),
                chamada.getData(),
                chamada.getProfessorId(),
                registros,
                chamada.getCreatedAt(),
                chamada.getUpdatedAt()
        );
    }
}
