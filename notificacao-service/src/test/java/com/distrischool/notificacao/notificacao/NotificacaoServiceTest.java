package com.distrischool.notificacao.notificacao;

import com.distrischool.notificacao.common.RecursoNaoEncontradoException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Sem Spring: repositório simulado e inserção em memória com a regra do índice único. */
class NotificacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");
    private static final Destinatario ALUNO_12 = Destinatario.de(Perfil.ALUNO, 12L);
    private static final Destinatario RESPONSAVEL_12 = Destinatario.de(Perfil.RESPONSAVEL, 12L);

    private NotificacaoRepository repository;
    private InsercaoEmMemoria insercao;
    private SimpleMeterRegistry registry;
    private ApplicationEventPublisher eventPublisher;
    private NotificacaoService service;

    @BeforeEach
    void preparar() {
        repository = mock(NotificacaoRepository.class);
        insercao = new InsercaoEmMemoria();
        registry = new SimpleMeterRegistry();
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new NotificacaoService(repository, insercao, new NotificacaoMetricas(registry), eventPublisher,
                mock(PlatformTransactionManager.class), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static NovaNotificacao nota(Destinatario destinatario) {
        return new NovaNotificacao(destinatario, TipoNotificacao.NOTA_LANCADA, "Nova nota",
                "Nova nota em Matemática (1º bimestre): 8,5", "/alunos/12");
    }

    private double criadas(TipoNotificacao tipo) {
        return registry.get("notificacoes.criadas").tag("tipo", tipo.name()).counter().count();
    }

    @Test
    void gravaUmaNotificacaoPorDestinatarioEContaNaMetrica() {
        List<NotificacaoCriada> criadas = service.registrar("hash-1", List.of(nota(ALUNO_12), nota(RESPONSAVEL_12)));

        assertThat(criadas).extracting(NotificacaoCriada::destinatario).containsExactly(ALUNO_12, RESPONSAVEL_12);
        assertThat(criadas).allSatisfy(c -> assertThat(c.criadaEm()).isEqualTo(AGORA));
        assertThat(insercao.gravadas()).hasSize(2);
        assertThat(criadas(TipoNotificacao.NOTA_LANCADA)).isEqualTo(2.0);
        assertThat(criadas(TipoNotificacao.FALTA_REGISTRADA)).isZero();
    }

    @Test
    void reentregaDoMesmoEventoNaoDuplicaNemContaNemPublicaDeNovo() {
        service.registrar("hash-1", List.of(nota(ALUNO_12), nota(RESPONSAVEL_12)));

        List<NotificacaoCriada> naReentrega = service.registrar("hash-1", List.of(nota(ALUNO_12), nota(RESPONSAVEL_12)));

        assertThat(naReentrega).isEmpty();
        assertThat(insercao.gravadas()).hasSize(2);
        assertThat(criadas(TipoNotificacao.NOTA_LANCADA)).isEqualTo(2.0);
        // Só as duas notificações da primeira entrega seguem para a entrega em tempo real
        verify(eventPublisher, times(2)).publishEvent(any(NotificacaoCriada.class));
    }

    @Test
    void outroEventoParaOMesmoDestinatarioGeraOutraNotificacao() {
        service.registrar("hash-1", List.of(nota(ALUNO_12)));

        assertThat(service.registrar("hash-2", List.of(nota(ALUNO_12)))).hasSize(1);
        assertThat(insercao.gravadas()).hasSize(2);
    }

    @Test
    void publicaCadaNotificacaoGravadaParaAEntregaEmTempoReal() {
        List<NotificacaoCriada> criadas = service.registrar("hash-1", List.of(nota(ALUNO_12)));

        verify(eventPublisher).publishEvent(criadas.get(0));
    }

    @Test
    void eventoSemNotificacoesNaoAbreTransacao() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        service = new NotificacaoService(repository, insercao, new NotificacaoMetricas(registry), eventPublisher,
                transactionManager, Clock.fixed(AGORA, ZoneOffset.UTC));

        assertThat(service.registrar("hash-1", List.of())).isEmpty();

        verifyNoInteractions(transactionManager, eventPublisher);
    }

    @Test
    @SuppressWarnings("unchecked")
    void marcaComoLidaEMantemADataDaPrimeiraLeitura() {
        Notificacao notificacao = new Notificacao(7L, RESPONSAVEL_12, TipoNotificacao.NOTA_LANCADA, "Nova nota",
                "Nova nota em Matemática (1º bimestre): 8,5", "/alunos/12", AGORA.minusSeconds(60), "hash-1");
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(notificacao));

        NotificacaoResponse resposta = service.marcarComoLida(RESPONSAVEL_12, 7L);

        assertThat(resposta.lida()).isTrue();
        assertThat(resposta.lidaEm()).isEqualTo(AGORA);

        notificacao.marcarComoLida(AGORA.plusSeconds(300));
        assertThat(notificacao.getLidaEm()).isEqualTo(AGORA);
    }

    @Test
    @SuppressWarnings("unchecked")
    void marcarComoLidaNotificacaoQueNaoEDoDestinatarioLancaNaoEncontrado() {
        // A busca filtra por id e destinatário: para quem não é o dono, não há resultado
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarComoLida(ALUNO_12, 7L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("7");
    }

    @Test
    void marcarTodasComoLidasUsaAReferenciaDoDestinatario() {
        when(repository.marcarTodasComoLidas(Perfil.RESPONSAVEL, 12L, AGORA)).thenReturn(4);

        assertThat(service.marcarTodasComoLidas(RESPONSAVEL_12)).isEqualTo(4);
    }

    @Test
    void marcarTodasComoLidasDaCoordenacaoNaoTemReferencia() {
        when(repository.marcarTodasComoLidasSemReferencia(Perfil.ADMIN, AGORA)).thenReturn(2);

        assertThat(service.marcarTodasComoLidas(Destinatario.admin())).isEqualTo(2);
    }
}
