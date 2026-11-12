package com.distrischool.notificacao;

import com.distrischool.notificacao.common.RecursoNaoEncontradoException;
import com.distrischool.notificacao.evento.KafkaTopics;
import com.distrischool.notificacao.notificacao.Destinatario;
import com.distrischool.notificacao.notificacao.NotificacaoResponse;
import com.distrischool.notificacao.notificacao.NotificacaoService;
import com.distrischool.notificacao.notificacao.Perfil;
import com.distrischool.notificacao.notificacao.TipoNotificacao;
import com.distrischool.notificacao.temporeal.EntregadorTempoReal;
import com.distrischool.notificacao.temporeal.NotificacaoCriadaMensagem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * O serviço inteiro contra um PostgreSQL 17 real (Testcontainers), com o test binder no lugar do
 * Kafka: migration do Flyway, validação do schema pelo Hibernate, índice único de idempotência
 * ({@code NULLS NOT DISTINCT} + {@code ON CONFLICT DO NOTHING}) e consultas por destinatário.
 * <p>
 * Precisa do Docker; sem ele, a classe é pulada e os demais testes (sem banco) continuam valendo.
 */
@SpringBootTest
@Import(TestChannelBinderConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class NotificacaoPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final Destinatario ALUNO_1 = Destinatario.de(Perfil.ALUNO, 1L);
    private static final Destinatario RESPONSAVEL_1 = Destinatario.de(Perfil.RESPONSAVEL, 1L);
    private static final Sort MAIS_RECENTES = Sort.by(Sort.Order.desc("criadaEm"), Sort.Order.desc("id"));

    private static final String SCHEDULE_UPDATED = """
            {"turmaId":12,"codigo":"9A-2026","nome":"%s","anoLetivo":2026,"turno":"MANHA","capacidade":35,
             "disciplinas":[{"disciplinaId":3,"codigo":"MAT","nome":"%s","professorId":7}],
             "acao":"ATUALIZADA","occurredAt":"%s"}
            """;
    private static final String GRADE_POSTED = """
            {"avaliacaoId":9,"turmaId":12,"disciplinaId":3,"bimestre":1,
             "notas":[{"alunoId":1,"valor":8.50},{"alunoId":2,"valor":6.00}],"occurredAt":"%s"}
            """;
    private static final String STUDENT_CREATED = """
            {"alunoId":1,"matricula":"2026000001","nome":"Ana Souza","email":"ana@example.com",
             "occurredAt":"2026-10-24T11:00:00Z"}
            """;

    @Autowired
    private InputDestination kafka;

    /** O entregador real, observado: sem sessões WebSocket abertas, ele só descarta os avisos. */
    @MockitoSpyBean
    private EntregadorTempoReal entregador;

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpar() {
        jdbc.update("TRUNCATE notificacao, turma_nome, disciplina_nome");
    }

    private void publicar(String topico, String chave, String payload) {
        kafka.send(MessageBuilder.withPayload(payload.getBytes(StandardCharsets.UTF_8))
                .setHeader(KafkaHeaders.RECEIVED_KEY, chave.getBytes(StandardCharsets.UTF_8))
                .build(), topico);
    }

    private List<NotificacaoResponse> notificacoesDe(Destinatario destinatario) {
        return notificacaoService.buscar(destinatario, false, PageRequest.of(0, 20, MAIS_RECENTES)).getContent();
    }

    private long linhas() {
        return jdbc.queryForObject("SELECT count(*) FROM notificacao", Long.class);
    }

    @Test
    void eventoViraNotificacaoComOsNomesDoCache() {
        publicar(KafkaTopics.SCHEDULE_UPDATED, "12",
                SCHEDULE_UPDATED.formatted("9º Ano A", "Matemática", "2026-10-24T10:00:00Z"));
        publicar(KafkaTopics.GRADE_POSTED, "12", GRADE_POSTED.formatted("2026-10-24T11:00:00Z"));

        assertThat(linhas()).isEqualTo(4);
        assertThat(notificacoesDe(RESPONSAVEL_1)).singleElement().satisfies(n -> {
            assertThat(n.tipo()).isEqualTo(TipoNotificacao.NOTA_LANCADA);
            assertThat(n.mensagem()).isEqualTo("Nova nota em Matemática (1º bimestre): 8,5");
            assertThat(n.link()).isEqualTo("/alunos/1");
            assertThat(n.lida()).isFalse();
            assertThat(n.criadaEm()).isNotNull();
        });
    }

    @Test
    void oMesmoEventoEntregueDuasVezesGeraUmaNotificacaoPorDestinatario() {
        String evento = GRADE_POSTED.formatted("2026-10-24T11:00:00Z");

        publicar(KafkaTopics.GRADE_POSTED, "12", evento);
        publicar(KafkaTopics.GRADE_POSTED, "12", evento);

        // 2 alunos x (ALUNO + RESPONSAVEL), uma vez só
        assertThat(linhas()).isEqualTo(4);
        assertThat(notificacoesDe(ALUNO_1)).hasSize(1);
        assertThat(notificacoesDe(RESPONSAVEL_1)).hasSize(1);

        // Outro evento (correção da nota): novas notificações
        publicar(KafkaTopics.GRADE_POSTED, "12", GRADE_POSTED.formatted("2026-10-25T09:00:00Z"));
        assertThat(linhas()).isEqualTo(8);
    }

    @Test
    void cadaNotificacaoGravadaGeraUmAvisoInternoQueChegaAoEntregador() {
        // No test binder, como no Kafka, o que é publicado em notificacao.criada volta para o
        // consumidor desse tópico: StreamBridge -> notificacao.criada -> EntregadorTempoReal
        String evento = GRADE_POSTED.formatted("2026-10-24T11:00:00Z");
        publicar(KafkaTopics.GRADE_POSTED, "12", evento);

        Long idDoResponsavel = notificacoesDe(RESPONSAVEL_1).get(0).id();
        verify(entregador).entregar(new NotificacaoCriadaMensagem(idDoResponsavel, Perfil.RESPONSAVEL, 1L));
        verify(entregador, times(4)).entregar(any());

        // Reentrega: nada foi gravado, então nenhum aviso novo
        publicar(KafkaTopics.GRADE_POSTED, "12", evento);
        verify(entregador, times(4)).entregar(any());

        // A coordenação não tem referência
        publicar(KafkaTopics.STUDENT_CREATED, "1", STUDENT_CREATED);
        Long idDaCoordenacao = notificacoesDe(Destinatario.admin()).get(0).id();
        verify(entregador).entregar(new NotificacaoCriadaMensagem(idDaCoordenacao, Perfil.ADMIN, null));
    }

    @Test
    void reentregaTambemNaoDuplicaAsNotificacoesDaCoordenacaoQueNaoTemReferencia() {
        // Sem NULLS NOT DISTINCT no índice único, as linhas com referência nula nunca conflitariam
        publicar(KafkaTopics.STUDENT_CREATED, "1", STUDENT_CREATED);
        publicar(KafkaTopics.STUDENT_CREATED, "1", STUDENT_CREATED);

        assertThat(notificacoesDe(Destinatario.admin())).singleElement()
                .extracting(NotificacaoResponse::mensagem)
                .isEqualTo("Aluno cadastrado: Ana Souza (matrícula 2026000001)");
    }

    @Test
    void cadaDestinatarioSoVeEAlteraAsProprias() {
        publicar(KafkaTopics.GRADE_POSTED, "12", GRADE_POSTED.formatted("2026-10-24T11:00:00Z"));
        publicar(KafkaTopics.GRADE_POSTED, "12", GRADE_POSTED.formatted("2026-10-25T09:00:00Z"));
        publicar(KafkaTopics.STUDENT_CREATED, "1", STUDENT_CREATED);
        Long doAluno = notificacoesDe(ALUNO_1).get(0).id();

        assertThat(notificacaoService.contarNaoLidas(ALUNO_1)).isEqualTo(2);
        assertThat(notificacaoService.contarNaoLidas(Destinatario.admin())).isEqualTo(1);
        assertThat(notificacaoService.contarNaoLidas(Destinatario.de(Perfil.PROFESSOR, 1L))).isZero();

        // Mesmo id de aluno, outro perfil: não é o dono
        assertThatThrownBy(() -> notificacaoService.marcarComoLida(RESPONSAVEL_1, doAluno))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> notificacaoService.marcarComoLida(Destinatario.admin(), doAluno))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(notificacaoService.contarNaoLidas(ALUNO_1)).isEqualTo(2);

        assertThat(notificacaoService.marcarComoLida(ALUNO_1, doAluno).lida()).isTrue();
        assertThat(notificacaoService.contarNaoLidas(ALUNO_1)).isEqualTo(1);
        assertThat(notificacaoService.buscar(ALUNO_1, true, PageRequest.of(0, 20, MAIS_RECENTES)).getContent())
                .extracting(NotificacaoResponse::id).doesNotContain(doAluno);
    }

    @Test
    void marcarTodasComoLidasSoAtingeODestinatario() {
        publicar(KafkaTopics.GRADE_POSTED, "12", GRADE_POSTED.formatted("2026-10-24T11:00:00Z"));
        publicar(KafkaTopics.GRADE_POSTED, "12", GRADE_POSTED.formatted("2026-10-25T09:00:00Z"));
        publicar(KafkaTopics.STUDENT_CREATED, "1", STUDENT_CREATED);

        assertThat(notificacaoService.marcarTodasComoLidas(ALUNO_1)).isEqualTo(2);
        assertThat(notificacaoService.marcarTodasComoLidas(ALUNO_1)).isZero();
        assertThat(notificacaoService.contarNaoLidas(ALUNO_1)).isZero();
        assertThat(notificacaoService.contarNaoLidas(RESPONSAVEL_1)).isEqualTo(2);
        assertThat(notificacaoService.contarNaoLidas(Destinatario.admin())).isEqualTo(1);

        assertThat(notificacaoService.marcarTodasComoLidas(Destinatario.admin())).isEqualTo(1);
        assertThat(notificacaoService.contarNaoLidas(Destinatario.admin())).isZero();
        assertThat(notificacaoService.contarNaoLidas(RESPONSAVEL_1)).isEqualTo(2);
    }

    @Test
    void listaVemDasMaisRecentesParaAsMaisAntigas() {
        publicar(KafkaTopics.GRADE_POSTED, "12", GRADE_POSTED.formatted("2026-10-24T11:00:00Z"));
        publicar(KafkaTopics.GRADE_POSTED, "12", GRADE_POSTED.formatted("2026-10-25T09:00:00Z"));
        publicar(KafkaTopics.GRADE_POSTED, "12", GRADE_POSTED.formatted("2026-10-26T09:00:00Z"));

        List<NotificacaoResponse> notificacoes = notificacoesDe(ALUNO_1);

        assertThat(notificacoes).hasSize(3);
        assertThat(notificacoes).extracting(NotificacaoResponse::id)
                .isSortedAccordingTo((a, b) -> Long.compare(b, a));
    }

    @Test
    void scheduleUpdatedAntigoNaoSobrescreveOCacheDeNomes() {
        publicar(KafkaTopics.SCHEDULE_UPDATED, "12",
                SCHEDULE_UPDATED.formatted("9º Ano A - Manhã", "Matemática I", "2026-10-24T11:05:00.123456789Z"));
        // Fora de ordem: evento anterior chega depois
        publicar(KafkaTopics.SCHEDULE_UPDATED, "12",
                SCHEDULE_UPDATED.formatted("9º Ano A", "Matemática", "2026-10-24T11:00:00Z"));
        // Reentrega do último (o banco guarda microssegundos; o evento tem nanossegundos)
        publicar(KafkaTopics.SCHEDULE_UPDATED, "12",
                SCHEDULE_UPDATED.formatted("9º Ano A - Manhã", "Matemática I", "2026-10-24T11:05:00.123456789Z"));

        assertThat(jdbc.queryForObject("SELECT nome FROM turma_nome WHERE turma_id = 12", String.class))
                .isEqualTo("9º Ano A - Manhã");
        assertThat(jdbc.queryForObject("SELECT nome FROM disciplina_nome WHERE disciplina_id = 3", String.class))
                .isEqualTo("Matemática I");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turma_nome", Long.class)).isEqualTo(1);
    }

    @Test
    void bancoRecusaPerfilTipoEReferenciaInvalidos() {
        String insert = """
                INSERT INTO notificacao (destinatario_perfil, destinatario_referencia_id, tipo, titulo, mensagem, evento_hash)
                VALUES (?, ?, ?, 't', 'm', 'h')
                """;

        assertThatThrownBy(() -> jdbc.update(insert, "VISITANTE", 1L, "NOTA_LANCADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "ALUNO", 1L, "OUTRO_TIPO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Só ADMIN fica sem referência
        assertThatThrownBy(() -> jdbc.update(insert, "ALUNO", null, "NOTA_LANCADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "ADMIN", 1L, "ALUNO_CADASTRADO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
