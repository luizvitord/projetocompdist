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
        List<DisciplinaOfertada> disciplinas,
        Instant createdAt,
        Instant updatedAt
) {

    public record ProfessorResumo(Long id, String nome) {
    }

    public record DisciplinaOfertada(
            Long disciplinaId,
            String codigo,
            String nome,
            Long professorId,
            String professorNome
    ) {
    }

    public static TurmaResponse from(Turma turma) {
        List<ProfessorResumo> professores = turma.getProfessores().stream()
                .map(p -> new ProfessorResumo(p.getId(), p.getNome()))
                .sorted(Comparator.comparing(ProfessorResumo::nome))
                .toList();
        List<DisciplinaOfertada> disciplinas = turma.getDisciplinas().stream()
                .map(td -> new DisciplinaOfertada(
                        td.getDisciplina().getId(),
                        td.getDisciplina().getCodigo(),
                        td.getDisciplina().getNome(),
                        td.getProfessor() != null ? td.getProfessor().getId() : null,
                        td.getProfessor() != null ? td.getProfessor().getNome() : null))
                .sorted(Comparator.comparing(DisciplinaOfertada::codigo))
                .toList();
        return new TurmaResponse(
                turma.getId(),
                turma.getCodigo(),
                turma.getNome(),
                turma.getAnoLetivo(),
                turma.getTurno(),
                turma.getCapacidade(),
                professores,
                disciplinas,
                turma.getCreatedAt(),
                turma.getUpdatedAt()
        );
    }
}
