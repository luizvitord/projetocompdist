package com.distrischool.notificacao.evento;

import com.distrischool.notificacao.common.MensagemInvalidaException;
import com.distrischool.notificacao.nomes.CatalogoNomes;
import com.distrischool.notificacao.notificacao.Destinatario;
import com.distrischool.notificacao.notificacao.NovaNotificacao;
import com.distrischool.notificacao.notificacao.Perfil;
import com.distrischool.notificacao.notificacao.TipoNotificacao;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Evento → notificações, para cada tópico. Os payloads têm o formato real dos produtores (ver
 * EventContractTest do gestao-escolar-service e os records de evento do gestao-administrativa).
 */
class MapeadorEventosTest {

    private static final Destinatario ALUNO_1 = Destinatario.de(Perfil.ALUNO, 1L);
    private static final Destinatario RESPONSAVEL_1 = Destinatario.de(Perfil.RESPONSAVEL, 1L);
    private static final Destinatario ALUNO_2 = Destinatario.de(Perfil.ALUNO, 2L);
    private static final Destinatario RESPONSAVEL_2 = Destinatario.de(Perfil.RESPONSAVEL, 2L);

    private final Map<Long, String> turmas = new HashMap<>(Map.of(12L, "9º Ano A", 15L, "9º Ano B"));
    private final Map<Long, String> disciplinas = new HashMap<>(Map.of(3L, "Matemática"));

    private final MapeadorEventos mapeador = new MapeadorEventos(new LeitorEventos(), new CatalogoNomes() {
        @Override
        public Optional<String> nomeTurma(Long turmaId) {
            return Optional.ofNullable(turmas.get(turmaId));
        }

        @Override
        public Optional<String> nomeDisciplina(Long disciplinaId) {
            return Optional.ofNullable(disciplinas.get(disciplinaId));
        }
    });

    private List<NovaNotificacao> mapear(String topico, String json) {
        return mapeador.mapear(topico, json.getBytes(StandardCharsets.UTF_8));
    }

    // --- grade.posted ---

    @Test
    void gradePostedNotificaAlunoEResponsavelDeCadaNota() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.GRADE_POSTED, """
                {"avaliacaoId":9,"turmaId":12,"disciplinaId":3,"bimestre":1,
                 "notas":[{"alunoId":1,"valor":8.50},{"alunoId":2,"valor":10.00}],
                 "occurredAt":"2026-10-24T11:00:00Z"}
                """);

        assertThat(notificacoes).extracting(NovaNotificacao::destinatario)
                .containsExactly(ALUNO_1, RESPONSAVEL_1, ALUNO_2, RESPONSAVEL_2);
        assertThat(notificacoes).allSatisfy(n -> {
            assertThat(n.tipo()).isEqualTo(TipoNotificacao.NOTA_LANCADA);
            assertThat(n.titulo()).isEqualTo("Nova nota");
        });
        assertThat(notificacoes.get(0).mensagem()).isEqualTo("Nova nota em Matemática (1º bimestre): 8,5");
        assertThat(notificacoes.get(0).link()).isEqualTo("/alunos/1");
        assertThat(notificacoes.get(2).mensagem()).isEqualTo("Nova nota em Matemática (1º bimestre): 10");
        assertThat(notificacoes.get(3).link()).isEqualTo("/alunos/2");
    }

    @Test
    void gradePostedSemNomeNoCacheUsaTextoNeutro() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.GRADE_POSTED, """
                {"avaliacaoId":9,"turmaId":12,"disciplinaId":77,"bimestre":2,
                 "notas":[{"alunoId":1,"valor":7.25}],"occurredAt":"2026-10-24T11:00:00Z"}
                """);

        assertThat(notificacoes.get(0).mensagem()).isEqualTo("Nova nota em disciplina #77 (2º bimestre): 7,25");
    }

    @Test
    void gradePostedComOMesmoAlunoRepetidoGeraUmaNotificacaoPorDestinatario() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.GRADE_POSTED, """
                {"avaliacaoId":9,"turmaId":12,"disciplinaId":3,"bimestre":1,
                 "notas":[{"alunoId":1,"valor":8},{"alunoId":1,"valor":9}],"occurredAt":"2026-10-24T11:00:00Z"}
                """);

        assertThat(notificacoes).extracting(NovaNotificacao::destinatario).containsExactly(ALUNO_1, RESPONSAVEL_1);
    }

    // --- attendance.recorded ---

    @Test
    void attendanceRecordedNotificaFaltasEAtrasos() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.ATTENDANCE_RECORDED, """
                {"chamadaId":500,"turmaId":12,"disciplinaId":3,"data":"2026-11-10",
                 "ausencias":[{"alunoId":1,"status":"FALTA"},{"alunoId":2,"status":"ATRASO"}],
                 "occurredAt":"2026-11-10T14:00:00Z"}
                """);

        assertThat(notificacoes).extracting(NovaNotificacao::destinatario)
                .containsExactly(ALUNO_1, RESPONSAVEL_1, ALUNO_2, RESPONSAVEL_2);
        assertThat(notificacoes).allSatisfy(n -> assertThat(n.tipo()).isEqualTo(TipoNotificacao.FALTA_REGISTRADA));
        assertThat(notificacoes.get(0).titulo()).isEqualTo("Falta registrada");
        assertThat(notificacoes.get(0).mensagem()).isEqualTo("Falta registrada em Matemática em 10/11/2026");
        assertThat(notificacoes.get(0).link()).isEqualTo("/alunos/1");
        assertThat(notificacoes.get(2).titulo()).isEqualTo("Atraso registrado");
        assertThat(notificacoes.get(2).mensagem()).isEqualTo("Atraso registrado em Matemática em 10/11/2026");
    }

    @Test
    void attendanceRecordedSemAusenciasNaoGeraNotificacao() {
        assertThat(mapear(KafkaTopics.ATTENDANCE_RECORDED, """
                {"chamadaId":500,"turmaId":12,"disciplinaId":3,"data":"2026-11-10","ausencias":[],
                 "occurredAt":"2026-11-10T14:00:00Z"}
                """)).isEmpty();
    }

    // --- enrollment.created ---

    @Test
    void enrollmentCreatedNotificaAlunoEResponsavel() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.ENROLLMENT_CREATED, """
                {"matriculaId":4,"alunoId":1,"turmaId":12,"anoLetivo":2026,"occurredAt":"2026-10-24T11:00:00Z"}
                """);

        assertThat(notificacoes).extracting(NovaNotificacao::destinatario).containsExactly(ALUNO_1, RESPONSAVEL_1);
        assertThat(notificacoes).allSatisfy(n -> {
            assertThat(n.tipo()).isEqualTo(TipoNotificacao.MATRICULA_CRIADA);
            assertThat(n.mensagem()).isEqualTo("Matrícula confirmada na turma 9º Ano A (2026)");
            assertThat(n.link()).isEqualTo("/alunos/1");
        });
    }

    @Test
    void enrollmentCreatedDeTurmaDesconhecidaUsaOId() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.ENROLLMENT_CREATED, """
                {"matriculaId":4,"alunoId":1,"turmaId":99,"anoLetivo":2026,"occurredAt":"2026-10-24T11:00:00Z"}
                """);

        assertThat(notificacoes.get(0).mensagem()).isEqualTo("Matrícula confirmada na turma #99 (2026)");
    }

    // --- enrollment.updated ---

    @Test
    void enrollmentUpdatedTrancadaNotificaSoAlunoEResponsavel() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.ENROLLMENT_UPDATED, """
                {"matriculaId":4,"alunoId":1,"turmaId":12,"statusAnterior":"ATIVA","statusNovo":"TRANCADA",
                 "turmaDestinoId":null,"occurredAt":"2026-10-24T11:00:00Z"}
                """);

        assertThat(notificacoes).extracting(NovaNotificacao::destinatario).containsExactly(ALUNO_1, RESPONSAVEL_1);
        assertThat(notificacoes).allSatisfy(n -> assertThat(n.tipo()).isEqualTo(TipoNotificacao.MATRICULA_ALTERADA));
        assertThat(notificacoes.get(0).mensagem()).isEqualTo("Sua matrícula na turma 9º Ano A foi trancada");
        assertThat(notificacoes.get(1).mensagem()).isEqualTo("A matrícula do aluno na turma 9º Ano A foi trancada");
    }

    @Test
    void enrollmentUpdatedCanceladaTambemAvisaACoordenacao() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.ENROLLMENT_UPDATED, """
                {"matriculaId":4,"alunoId":1,"turmaId":12,"statusAnterior":"TRANCADA","statusNovo":"CANCELADA",
                 "turmaDestinoId":null,"occurredAt":"2026-10-24T11:00:00Z"}
                """);

        assertThat(notificacoes).extracting(NovaNotificacao::destinatario)
                .containsExactly(ALUNO_1, RESPONSAVEL_1, Destinatario.admin());
        assertThat(notificacoes.get(0).mensagem()).isEqualTo("Sua matrícula na turma 9º Ano A foi cancelada");
        assertThat(notificacoes.get(2).mensagem()).isEqualTo("A matrícula do aluno #1 na turma 9º Ano A foi cancelada");
        assertThat(notificacoes.get(2).link()).isEqualTo("/alunos/1");
    }

    @Test
    void enrollmentUpdatedTransferidaCitaATurmaDeDestinoEAvisaACoordenacao() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.ENROLLMENT_UPDATED, """
                {"matriculaId":4,"alunoId":1,"turmaId":12,"statusAnterior":"ATIVA","statusNovo":"TRANSFERIDA",
                 "turmaDestinoId":15,"occurredAt":"2026-10-24T11:00:00Z"}
                """);

        assertThat(notificacoes).extracting(NovaNotificacao::destinatario)
                .containsExactly(ALUNO_1, RESPONSAVEL_1, Destinatario.admin());
        assertThat(notificacoes.get(0).mensagem())
                .isEqualTo("Sua matrícula na turma 9º Ano A foi transferida para a turma 9º Ano B");
    }

    @Test
    void enrollmentUpdatedReativadaEConcluidaEStatusDesconhecido() {
        String modelo = """
                {"matriculaId":4,"alunoId":1,"turmaId":12,"statusAnterior":"TRANCADA","statusNovo":"%s",
                 "turmaDestinoId":null,"occurredAt":"2026-10-24T11:00:00Z"}
                """;

        assertThat(mapear(KafkaTopics.ENROLLMENT_UPDATED, modelo.formatted("ATIVA")).get(0).mensagem())
                .isEqualTo("Sua matrícula na turma 9º Ano A foi reativada");
        assertThat(mapear(KafkaTopics.ENROLLMENT_UPDATED, modelo.formatted("CONCLUIDA")).get(0).mensagem())
                .isEqualTo("Sua matrícula na turma 9º Ano A foi concluída");
        // Um status que o produtor venha a criar não quebra o consumidor
        assertThat(mapear(KafkaTopics.ENROLLMENT_UPDATED, modelo.formatted("SUSPENSA")))
                .hasSize(2)
                .first().extracting(NovaNotificacao::mensagem)
                .isEqualTo("Sua matrícula na turma 9º Ano A mudou de situação");
    }

    // --- student.created ---

    @Test
    void studentCreatedNotificaACoordenacao() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.STUDENT_CREATED, """
                {"alunoId":1,"matricula":"2026000001","nome":"Ana Souza","email":"ana@example.com",
                 "occurredAt":"2026-10-24T11:00:00Z"}
                """);

        assertThat(notificacoes).containsExactly(new NovaNotificacao(Destinatario.admin(),
                TipoNotificacao.ALUNO_CADASTRADO, "Aluno cadastrado",
                "Aluno cadastrado: Ana Souza (matrícula 2026000001)", "/alunos/1"));
    }

    // --- teacher.assigned ---

    @Test
    void teacherAssignedNotificaOProfessor() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.TEACHER_ASSIGNED, """
                {"professorId":7,"professorNome":"Maria Lima","professorEmail":"maria@example.com",
                 "turmaId":12,"turmaCodigo":"9A-2026","turmaNome":"9º Ano A","occurredAt":"2026-10-24T11:00:00Z"}
                """);

        assertThat(notificacoes).containsExactly(new NovaNotificacao(Destinatario.de(Perfil.PROFESSOR, 7L),
                TipoNotificacao.PROFESSOR_ATRIBUIDO, "Nova turma atribuída",
                "Você foi atribuído à turma 9º Ano A", "/turmas/12"));
    }

    // --- staff.created ---

    @Test
    void staffCreatedNotificaACoordenacao() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.STAFF_CREATED, """
                {"tecnicoId":5,"nome":"João Lima","email":"joao@example.com","cargo":"SECRETARIO",
                 "setor":"Secretaria","occurredAt":"2026-10-24T11:00:00Z"}
                """);

        assertThat(notificacoes).containsExactly(new NovaNotificacao(Destinatario.admin(),
                TipoNotificacao.TECNICO_CADASTRADO, "Técnico cadastrado",
                "Técnico administrativo cadastrado: João Lima (Secretaria)", "/pessoas"));
    }

    // --- contrato ---

    @Test
    void ignoraCamposDesconhecidos() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.ENROLLMENT_CREATED, """
                {"matriculaId":4,"alunoId":1,"turmaId":12,"anoLetivo":2026,"campoNovo":{"x":[1,2]},
                 "occurredAt":"2026-10-24T11:00:00Z"}
                """);

        assertThat(notificacoes).hasSize(2);
    }

    @Test
    void payloadForaDoContratoLancaMensagemInvalidaSemCitarOConteudo() {
        assertThatThrownBy(() -> mapear(KafkaTopics.GRADE_POSTED, "{ isto não é json de Ana Souza"))
                .isInstanceOf(MensagemInvalidaException.class)
                .hasMessageStartingWith("grade.posted: JSON inválido")
                .hasMessageNotContaining("Ana Souza");
        assertThatThrownBy(() -> mapear(KafkaTopics.GRADE_POSTED, "\"texto\""))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> mapear(KafkaTopics.GRADE_POSTED, "null"))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> mapear(KafkaTopics.GRADE_POSTED, """
                {"avaliacaoId":9,"turmaId":12,"bimestre":1,"notas":[{"alunoId":1,"valor":8}]}
                """))
                .isInstanceOf(MensagemInvalidaException.class)
                .hasMessage("grade.posted sem o campo obrigatório disciplinaId");
        assertThatThrownBy(() -> mapear(KafkaTopics.GRADE_POSTED, """
                {"avaliacaoId":9,"turmaId":12,"disciplinaId":3,"bimestre":1,"notas":[{"valor":8}]}
                """))
                .isInstanceOf(MensagemInvalidaException.class)
                .hasMessage("grade.posted sem o campo obrigatório notas[].alunoId");
        assertThatThrownBy(() -> mapear(KafkaTopics.ATTENDANCE_RECORDED, """
                {"chamadaId":500,"turmaId":12,"disciplinaId":3,"ausencias":[]}
                """))
                .isInstanceOf(MensagemInvalidaException.class)
                .hasMessage("attendance.recorded sem o campo obrigatório data");
        assertThatThrownBy(() -> mapear(KafkaTopics.ENROLLMENT_UPDATED, """
                {"matriculaId":4,"alunoId":1,"turmaId":12,"statusAnterior":"ATIVA"}
                """))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> mapear(KafkaTopics.STUDENT_CREATED, """
                {"matricula":"2026000001","nome":"Ana Souza"}
                """))
                .isInstanceOf(MensagemInvalidaException.class)
                .hasMessageNotContaining("Ana Souza");
        assertThatThrownBy(() -> mapear(KafkaTopics.TEACHER_ASSIGNED, "{\"turmaId\":12}"))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> mapear(KafkaTopics.STAFF_CREATED, "{\"tecnicoId\":5}"))
                .isInstanceOf(MensagemInvalidaException.class);
    }

    @Test
    void mensagemLongaETruncadaNoTamanhoDaColuna() {
        List<NovaNotificacao> notificacoes = mapear(KafkaTopics.STUDENT_CREATED,
                "{\"alunoId\":1,\"matricula\":\"2026000001\",\"nome\":\"" + "A".repeat(600) + "\"}");

        assertThat(notificacoes.get(0).mensagem()).hasSize(MapeadorEventos.TAMANHO_MAXIMO_MENSAGEM).endsWith("…");
    }
}
