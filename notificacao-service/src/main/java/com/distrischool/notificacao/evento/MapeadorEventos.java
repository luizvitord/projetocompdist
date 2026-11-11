package com.distrischool.notificacao.evento;

import com.distrischool.notificacao.common.MensagemInvalidaException;
import com.distrischool.notificacao.evento.Mensagens.AttendanceRecorded;
import com.distrischool.notificacao.evento.Mensagens.EnrollmentCreated;
import com.distrischool.notificacao.evento.Mensagens.EnrollmentUpdated;
import com.distrischool.notificacao.evento.Mensagens.GradePosted;
import com.distrischool.notificacao.evento.Mensagens.StaffCreated;
import com.distrischool.notificacao.evento.Mensagens.StudentCreated;
import com.distrischool.notificacao.evento.Mensagens.TeacherAssigned;
import com.distrischool.notificacao.nomes.CatalogoNomes;
import com.distrischool.notificacao.notificacao.Destinatario;
import com.distrischool.notificacao.notificacao.NovaNotificacao;
import com.distrischool.notificacao.notificacao.Perfil;
import com.distrischool.notificacao.notificacao.TipoNotificacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transforma um evento de domínio nas notificações que ele gera: quem recebe e com que texto.
 * <p>
 * Notas, frequência e matrícula vão para o aluno e para o responsável dele (o mesmo
 * {@code alunoId} com os perfis ALUNO e RESPONSAVEL); cadastros vão para a coordenação (ADMIN).
 * Os nomes de turmas e disciplinas vêm do {@link CatalogoNomes}; se ainda não forem conhecidos, o
 * texto usa o id ("turma #12").
 * <p>
 * No máximo uma notificação por destinatário para cada evento: é o que o índice único de
 * idempotência (hash do evento + destinatário) permite gravar.
 */
@Component
@RequiredArgsConstructor
public class MapeadorEventos {

    static final int TAMANHO_MAXIMO_MENSAGEM = 500;

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Mudanças de matrícula que a coordenação também acompanha. */
    private static final Set<String> STATUS_AVISADOS_A_COORDENACAO = Set.of("CANCELADA", "TRANSFERIDA");

    private final LeitorEventos leitor;
    private final CatalogoNomes nomes;

    /**
     * @throws MensagemInvalidaException se o payload estiver fora do contrato do tópico
     */
    public List<NovaNotificacao> mapear(String topico, byte[] payload) {
        List<NovaNotificacao> notificacoes = switch (topico) {
            case KafkaTopics.GRADE_POSTED -> notaLancada(leitor.ler(topico, payload, GradePosted.class));
            case KafkaTopics.ATTENDANCE_RECORDED ->
                    faltaRegistrada(leitor.ler(topico, payload, AttendanceRecorded.class));
            case KafkaTopics.ENROLLMENT_CREATED ->
                    matriculaCriada(leitor.ler(topico, payload, EnrollmentCreated.class));
            case KafkaTopics.ENROLLMENT_UPDATED ->
                    matriculaAlterada(leitor.ler(topico, payload, EnrollmentUpdated.class));
            case KafkaTopics.STUDENT_CREATED -> alunoCadastrado(leitor.ler(topico, payload, StudentCreated.class));
            case KafkaTopics.TEACHER_ASSIGNED ->
                    professorAtribuido(leitor.ler(topico, payload, TeacherAssigned.class));
            case KafkaTopics.STAFF_CREATED -> tecnicoCadastrado(leitor.ler(topico, payload, StaffCreated.class));
            default -> throw new IllegalArgumentException("Tópico sem mapeamento de notificações: " + topico);
        };
        return umaPorDestinatario(notificacoes);
    }

    private List<NovaNotificacao> notaLancada(GradePosted evento) {
        exigir(KafkaTopics.GRADE_POSTED, "turmaId", evento.turmaId());
        exigir(KafkaTopics.GRADE_POSTED, "disciplinaId", evento.disciplinaId());
        exigir(KafkaTopics.GRADE_POSTED, "notas", evento.notas());

        String disciplina = disciplina(evento.disciplinaId());
        String bimestre = evento.bimestre() != null ? " (" + evento.bimestre() + "º bimestre)" : "";
        List<NovaNotificacao> notificacoes = new ArrayList<>();
        for (GradePosted.NotaAluno nota : evento.notas()) {
            exigir(KafkaTopics.GRADE_POSTED, "notas[].alunoId", nota != null ? nota.alunoId() : null);
            exigir(KafkaTopics.GRADE_POSTED, "notas[].valor", nota.valor());
            String mensagem = "Nova nota em " + disciplina + bimestre + ": " + decimal(nota.valor());
            paraAlunoEResponsavel(notificacoes, nota.alunoId(), TipoNotificacao.NOTA_LANCADA, "Nova nota", mensagem);
        }
        return notificacoes;
    }

    private List<NovaNotificacao> faltaRegistrada(AttendanceRecorded evento) {
        exigir(KafkaTopics.ATTENDANCE_RECORDED, "turmaId", evento.turmaId());
        exigir(KafkaTopics.ATTENDANCE_RECORDED, "disciplinaId", evento.disciplinaId());
        exigir(KafkaTopics.ATTENDANCE_RECORDED, "data", evento.data());
        exigir(KafkaTopics.ATTENDANCE_RECORDED, "ausencias", evento.ausencias());

        String complemento = " em " + disciplina(evento.disciplinaId()) + " em " + DATA.format(evento.data());
        List<NovaNotificacao> notificacoes = new ArrayList<>();
        for (AttendanceRecorded.Ausencia ausencia : evento.ausencias()) {
            exigir(KafkaTopics.ATTENDANCE_RECORDED, "ausencias[].alunoId", ausencia != null ? ausencia.alunoId() : null);
            exigir(KafkaTopics.ATTENDANCE_RECORDED, "ausencias[].status", ausencia.status());
            // Um status novo no produtor não trava o consumidor: vira um aviso genérico
            String titulo = switch (ausencia.status()) {
                case "FALTA" -> "Falta registrada";
                case "ATRASO" -> "Atraso registrado";
                default -> "Ocorrência de frequência registrada";
            };
            paraAlunoEResponsavel(notificacoes, ausencia.alunoId(), TipoNotificacao.FALTA_REGISTRADA, titulo,
                    titulo + complemento);
        }
        return notificacoes;
    }

    private List<NovaNotificacao> matriculaCriada(EnrollmentCreated evento) {
        exigir(KafkaTopics.ENROLLMENT_CREATED, "alunoId", evento.alunoId());
        exigir(KafkaTopics.ENROLLMENT_CREATED, "turmaId", evento.turmaId());

        String ano = evento.anoLetivo() != null ? " (" + evento.anoLetivo() + ")" : "";
        String mensagem = "Matrícula confirmada na turma " + turma(evento.turmaId()) + ano;
        List<NovaNotificacao> notificacoes = new ArrayList<>();
        paraAlunoEResponsavel(notificacoes, evento.alunoId(), TipoNotificacao.MATRICULA_CRIADA,
                "Matrícula confirmada", mensagem);
        return notificacoes;
    }

    private List<NovaNotificacao> matriculaAlterada(EnrollmentUpdated evento) {
        exigir(KafkaTopics.ENROLLMENT_UPDATED, "alunoId", evento.alunoId());
        exigir(KafkaTopics.ENROLLMENT_UPDATED, "turmaId", evento.turmaId());
        exigir(KafkaTopics.ENROLLMENT_UPDATED, "statusNovo", evento.statusNovo());

        String naTurma = " na turma " + turma(evento.turmaId()) + " " + mudanca(evento);
        String link = linkAluno(evento.alunoId());
        String titulo = "Matrícula alterada";
        List<NovaNotificacao> notificacoes = new ArrayList<>();
        notificacoes.add(nova(Destinatario.de(Perfil.ALUNO, evento.alunoId()), TipoNotificacao.MATRICULA_ALTERADA,
                titulo, "Sua matrícula" + naTurma, link));
        notificacoes.add(nova(Destinatario.de(Perfil.RESPONSAVEL, evento.alunoId()), TipoNotificacao.MATRICULA_ALTERADA,
                titulo, "A matrícula do aluno" + naTurma, link));
        if (STATUS_AVISADOS_A_COORDENACAO.contains(evento.statusNovo())) {
            // Só o id: o evento não traz o nome do aluno
            notificacoes.add(nova(Destinatario.admin(), TipoNotificacao.MATRICULA_ALTERADA, titulo,
                    "A matrícula do aluno #" + evento.alunoId() + naTurma, link));
        }
        return notificacoes;
    }

    private List<NovaNotificacao> alunoCadastrado(StudentCreated evento) {
        exigir(KafkaTopics.STUDENT_CREATED, "alunoId", evento.alunoId());
        exigir(KafkaTopics.STUDENT_CREATED, "nome", evento.nome());

        String matricula = evento.matricula() != null ? " (matrícula " + evento.matricula() + ")" : "";
        return List.of(nova(Destinatario.admin(), TipoNotificacao.ALUNO_CADASTRADO, "Aluno cadastrado",
                "Aluno cadastrado: " + evento.nome() + matricula, linkAluno(evento.alunoId())));
    }

    private List<NovaNotificacao> professorAtribuido(TeacherAssigned evento) {
        exigir(KafkaTopics.TEACHER_ASSIGNED, "professorId", evento.professorId());
        exigir(KafkaTopics.TEACHER_ASSIGNED, "turmaId", evento.turmaId());

        // O próprio evento traz o nome da turma; o cache é só a segunda opção
        String turma = evento.turmaNome() != null ? evento.turmaNome() : turma(evento.turmaId());
        return List.of(nova(Destinatario.de(Perfil.PROFESSOR, evento.professorId()),
                TipoNotificacao.PROFESSOR_ATRIBUIDO, "Nova turma atribuída",
                "Você foi atribuído à turma " + turma, "/turmas/" + evento.turmaId()));
    }

    private List<NovaNotificacao> tecnicoCadastrado(StaffCreated evento) {
        exigir(KafkaTopics.STAFF_CREATED, "tecnicoId", evento.tecnicoId());
        exigir(KafkaTopics.STAFF_CREATED, "nome", evento.nome());

        String setor = evento.setor() != null && !evento.setor().isBlank() ? " (" + evento.setor() + ")" : "";
        return List.of(nova(Destinatario.admin(), TipoNotificacao.TECNICO_CADASTRADO, "Técnico cadastrado",
                "Técnico administrativo cadastrado: " + evento.nome() + setor, "/pessoas"));
    }

    private String mudanca(EnrollmentUpdated evento) {
        return switch (evento.statusNovo()) {
            case "ATIVA" -> "foi reativada";
            case "TRANCADA" -> "foi trancada";
            case "CANCELADA" -> "foi cancelada";
            case "CONCLUIDA" -> "foi concluída";
            case "TRANSFERIDA" -> evento.turmaDestinoId() != null
                    ? "foi transferida para a turma " + turma(evento.turmaDestinoId())
                    : "foi transferida";
            // Um status novo no produtor não trava o consumidor
            default -> "mudou de situação";
        };
    }

    private String turma(Long turmaId) {
        return nomes.nomeTurma(turmaId).orElse("#" + turmaId);
    }

    private String disciplina(Long disciplinaId) {
        return nomes.nomeDisciplina(disciplinaId).orElse("disciplina #" + disciplinaId);
    }

    private static void paraAlunoEResponsavel(List<NovaNotificacao> notificacoes, Long alunoId, TipoNotificacao tipo,
                                              String titulo, String mensagem) {
        String link = linkAluno(alunoId);
        notificacoes.add(nova(Destinatario.de(Perfil.ALUNO, alunoId), tipo, titulo, mensagem, link));
        notificacoes.add(nova(Destinatario.de(Perfil.RESPONSAVEL, alunoId), tipo, titulo, mensagem, link));
    }

    private static NovaNotificacao nova(Destinatario destinatario, TipoNotificacao tipo, String titulo,
                                        String mensagem, String link) {
        String texto = mensagem.length() > TAMANHO_MAXIMO_MENSAGEM
                ? mensagem.substring(0, TAMANHO_MAXIMO_MENSAGEM - 1) + "…"
                : mensagem;
        return new NovaNotificacao(destinatario, tipo, titulo, texto, link);
    }

    private static String linkAluno(Long alunoId) {
        return "/alunos/" + alunoId;
    }

    /** {@code 8.50} vira "8,5" e {@code 10.00} vira "10". */
    private static String decimal(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static List<NovaNotificacao> umaPorDestinatario(List<NovaNotificacao> notificacoes) {
        Map<Destinatario, NovaNotificacao> porDestinatario = new LinkedHashMap<>();
        notificacoes.forEach(n -> porDestinatario.putIfAbsent(n.destinatario(), n));
        return List.copyOf(porDestinatario.values());
    }

    private static void exigir(String topico, String campo, Object valor) {
        if (valor == null) {
            throw new MensagemInvalidaException(topico + " sem o campo obrigatório " + campo);
        }
    }
}
