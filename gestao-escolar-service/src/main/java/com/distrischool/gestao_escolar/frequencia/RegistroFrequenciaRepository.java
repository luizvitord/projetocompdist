package com.distrischool.gestao_escolar.frequencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Contagens de presença agregadas no banco (uma linha por aluno ou por disciplina). */
public interface RegistroFrequenciaRepository extends JpaRepository<RegistroFrequencia, Long> {

    record ContagemPorAluno(Long alunoId, String numeroMatricula, String nome,
                            long presencas, long faltas, long atrasos) {
    }

    record ContagemPorDisciplina(Long disciplinaId, String codigo, String nome,
                                 long presencas, long faltas, long atrasos) {
    }

    record ContagemPorMatriculaEDisciplina(Long matriculaId, Long disciplinaId,
                                           long presencas, long faltas, long atrasos) {
    }

    @Query("""
            select new com.distrischool.gestao_escolar.frequencia.RegistroFrequenciaRepository$ContagemPorAluno(
                a.id, a.matricula, a.nome,
                sum(case when r.status = com.distrischool.gestao_escolar.frequencia.StatusPresenca.PRESENTE then 1 else 0 end),
                sum(case when r.status = com.distrischool.gestao_escolar.frequencia.StatusPresenca.FALTA then 1 else 0 end),
                sum(case when r.status = com.distrischool.gestao_escolar.frequencia.StatusPresenca.ATRASO then 1 else 0 end))
            from RegistroFrequencia r
                join r.chamada c
                join com.distrischool.gestao_escolar.matricula.Matricula m on m.id = r.matriculaId
                join com.distrischool.gestao_escolar.aluno.Aluno a on a.id = m.alunoId
            where c.turmaId = :turmaId
                and (:disciplinaId is null or c.disciplinaId = :disciplinaId)
                and c.data between :inicio and :fim
            group by a.id, a.matricula, a.nome
            order by a.nome, a.id
            """)
    List<ContagemPorAluno> contarPorAluno(@Param("turmaId") Long turmaId,
                                          @Param("disciplinaId") Long disciplinaId,
                                          @Param("inicio") LocalDate inicio,
                                          @Param("fim") LocalDate fim);

    /** Todas as matrículas do aluno no ano letivo (inclusive encerradas), agrupadas por disciplina. */
    @Query("""
            select new com.distrischool.gestao_escolar.frequencia.RegistroFrequenciaRepository$ContagemPorDisciplina(
                c.disciplinaId, max(d.codigo), max(d.nome),
                sum(case when r.status = com.distrischool.gestao_escolar.frequencia.StatusPresenca.PRESENTE then 1 else 0 end),
                sum(case when r.status = com.distrischool.gestao_escolar.frequencia.StatusPresenca.FALTA then 1 else 0 end),
                sum(case when r.status = com.distrischool.gestao_escolar.frequencia.StatusPresenca.ATRASO then 1 else 0 end))
            from RegistroFrequencia r
                join r.chamada c
                join com.distrischool.gestao_escolar.matricula.Matricula m on m.id = r.matriculaId
                join com.distrischool.gestao_escolar.turma.TurmaDisciplinaReplica d
                    on d.id.turmaId = c.turmaId and d.id.disciplinaId = c.disciplinaId
            where m.alunoId = :alunoId and m.anoLetivo = :anoLetivo
            group by c.disciplinaId
            order by max(d.codigo)
            """)
    List<ContagemPorDisciplina> contarPorDisciplina(@Param("alunoId") Long alunoId,
                                                    @Param("anoLetivo") Integer anoLetivo);

    /** Contagens por matrícula e disciplina, para o boletim. */
    @Query("""
            select new com.distrischool.gestao_escolar.frequencia.RegistroFrequenciaRepository$ContagemPorMatriculaEDisciplina(
                r.matriculaId, c.disciplinaId,
                sum(case when r.status = com.distrischool.gestao_escolar.frequencia.StatusPresenca.PRESENTE then 1 else 0 end),
                sum(case when r.status = com.distrischool.gestao_escolar.frequencia.StatusPresenca.FALTA then 1 else 0 end),
                sum(case when r.status = com.distrischool.gestao_escolar.frequencia.StatusPresenca.ATRASO then 1 else 0 end))
            from RegistroFrequencia r join r.chamada c
            where r.matriculaId in :matriculaIds
            group by r.matriculaId, c.disciplinaId
            """)
    List<ContagemPorMatriculaEDisciplina> contarPorMatriculaEDisciplina(
            @Param("matriculaIds") Collection<Long> matriculaIds);
}
