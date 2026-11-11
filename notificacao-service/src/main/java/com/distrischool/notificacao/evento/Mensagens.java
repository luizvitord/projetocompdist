package com.distrischool.notificacao.evento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Leitura local dos eventos dos outros serviços. Não depende das classes dos produtores: cada
 * record traz só os campos usados para montar as notificações, e o resto do JSON é ignorado
 * (inclusive emails, que este serviço não precisa guardar).
 * <p>
 * Os contratos estão nos records de {@code gestao-escolar-service/.../event/} e
 * {@code gestao-administrativa-service/.../event/}.
 */
public final class Mensagens {

    private Mensagens() {
    }

    /** {@code grade.posted}: lançamento em lote ou correção de uma nota. */
    public record GradePosted(Long avaliacaoId, Long turmaId, Long disciplinaId, Integer bimestre,
                              List<NotaAluno> notas) {

        public record NotaAluno(Long alunoId, BigDecimal valor) {
        }
    }

    /** {@code attendance.recorded}: {@code ausencias} traz só FALTA e ATRASO. */
    public record AttendanceRecorded(Long chamadaId, Long turmaId, Long disciplinaId, LocalDate data,
                                     List<Ausencia> ausencias) {

        public record Ausencia(Long alunoId, String status) {
        }
    }

    /** {@code enrollment.created}. */
    public record EnrollmentCreated(Long matriculaId, Long alunoId, Long turmaId, Integer anoLetivo) {
    }

    /** {@code enrollment.updated}: {@code turmaDestinoId} só vem em transferências. */
    public record EnrollmentUpdated(Long matriculaId, Long alunoId, Long turmaId, String statusAnterior,
                                    String statusNovo, Long turmaDestinoId) {
    }

    /** {@code student.created}: {@code matricula} é o número de matrícula do aluno. */
    public record StudentCreated(Long alunoId, String matricula, String nome) {
    }

    /** {@code teacher.assigned}. */
    public record TeacherAssigned(Long professorId, Long turmaId, String turmaCodigo, String turmaNome) {
    }

    /** {@code staff.created}. */
    public record StaffCreated(Long tecnicoId, String nome, String cargo, String setor) {
    }
}
