package com.distrischool.gestao_administrativa.turma;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record TurmaResponse(
        Long id,
        String codigo,
        String nome,
        Integer anoLetivo,
        Turno turno,
        Integer capacidade,
        List<ProfessorResumo> professores,
        Instant createdAt,
        Instant updatedAt
) {

    public record ProfessorResumo(Long id, String nome) {
    }

    public static TurmaResponse from(Turma turma) {
        List<ProfessorResumo> professores = turma.getProfessores().stream()
                .map(p -> new ProfessorResumo(p.getId(), p.getNome()))
                .sorted(Comparator.comparing(ProfessorResumo::nome))
                .toList();
        return new TurmaResponse(
                turma.getId(),
                turma.getCodigo(),
                turma.getNome(),
                turma.getAnoLetivo(),
                turma.getTurno(),
                turma.getCapacidade(),
                professores,
                turma.getCreatedAt(),
                turma.getUpdatedAt()
        );
    }
}
