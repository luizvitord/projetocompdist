package com.distrischool.notificacao.temporeal;

import com.distrischool.notificacao.notificacao.Destinatario;
import com.distrischool.notificacao.notificacao.NotificacaoMetricas;
import com.distrischool.notificacao.notificacao.NotificacaoResponse;
import com.distrischool.notificacao.notificacao.NotificacaoService;
import com.distrischool.notificacao.notificacao.Perfil;
import com.distrischool.notificacao.notificacao.TipoNotificacao;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Entrega do aviso às sessões desta réplica; registro de sessões e banco simulados. */
class EntregadorTempoRealTest {

    private static final Destinatario RESPONSAVEL_12 = Destinatario.de(Perfil.RESPONSAVEL, 12L);

    private SimpUserRegistry sessoes;
    private SimpMessagingTemplate websocket;
    private NotificacaoService notificacaoService;
    private SimpleMeterRegistry registry;
    private EntregadorTempoReal entregador;

    @BeforeEach
    void preparar() {
        sessoes = mock(SimpUserRegistry.class);
        websocket = mock(SimpMessagingTemplate.class);
        notificacaoService = mock(NotificacaoService.class);
        registry = new SimpleMeterRegistry();
        entregador = new EntregadorTempoReal(sessoes, websocket, notificacaoService, new NotificacaoMetricas(registry));
    }

    private void conectado(String nome, int quantidadeDeSessoes) {
        SimpUser usuario = mock(SimpUser.class);
        Set<SimpSession> sessoesDoUsuario = new HashSet<>();
        for (int i = 0; i < quantidadeDeSessoes; i++) {
            sessoesDoUsuario.add(mock(SimpSession.class));
        }
        when(usuario.getName()).thenReturn(nome);
        when(usuario.getSessions()).thenReturn(sessoesDoUsuario);
        when(sessoes.getUser(nome)).thenReturn(usuario);
    }

    private static NotificacaoResponse notificacao(Long id) {
        return new NotificacaoResponse(id, TipoNotificacao.NOTA_LANCADA, "Nova nota",
                "Nova nota em Matemática (1º bimestre): 8,5", "/alunos/12", Instant.parse("2026-11-10T11:00:00Z"),
                null, false);
    }

    private double enviadas() {
        return registry.get("notificacoes.enviadas").tag("canal", "websocket").counter().count();
    }

    @Test
    void entregaAoUsuarioConectadoNestaReplica() {
        conectado("RESPONSAVEL:12", 1);
        when(notificacaoService.buscarPorId(RESPONSAVEL_12, 7L)).thenReturn(Optional.of(notificacao(7L)));

        entregador.entregar(new NotificacaoCriadaMensagem(7L, Perfil.RESPONSAVEL, 12L));

        verify(websocket).convertAndSendToUser("RESPONSAVEL:12", "/queue/notificacoes", notificacao(7L));
        assertThat(enviadas()).isEqualTo(1.0);
    }

    @Test
    void contaUmaEntregaPorSessaoDoUsuario() {
        // Duas abas abertas pelo mesmo usuário
        conectado("RESPONSAVEL:12", 2);
        when(notificacaoService.buscarPorId(RESPONSAVEL_12, 7L)).thenReturn(Optional.of(notificacao(7L)));

        entregador.entregar(new NotificacaoCriadaMensagem(7L, Perfil.RESPONSAVEL, 12L));

        assertThat(enviadas()).isEqualTo(2.0);
    }

    @Test
    void semSessaoNestaReplicaNaoConsultaOBancoNemEnvia() {
        // Outro usuário está conectado; o destinatário do aviso, não
        conectado("ALUNO:12", 1);

        entregador.entregar(new NotificacaoCriadaMensagem(7L, Perfil.RESPONSAVEL, 12L));

        verifyNoInteractions(notificacaoService, websocket);
        assertThat(enviadas()).isZero();
    }

    @Test
    void avisoDaCoordenacaoVaiParaOUsuarioAdmin() {
        conectado("ADMIN", 1);
        when(notificacaoService.buscarPorId(Destinatario.admin(), 9L)).thenReturn(Optional.of(notificacao(9L)));

        entregador.entregar(new NotificacaoCriadaMensagem(9L, Perfil.ADMIN, null));

        verify(websocket).convertAndSendToUser("ADMIN", "/queue/notificacoes", notificacao(9L));
    }

    @Test
    void naoEntregaNotificacaoQueNaoEDoDestinatarioDoAviso() {
        conectado("RESPONSAVEL:12", 1);
        // A busca por id + destinatário não encontra: a notificação 7 é de outra pessoa (ou não existe)
        when(notificacaoService.buscarPorId(RESPONSAVEL_12, 7L)).thenReturn(Optional.empty());

        entregador.entregar(new NotificacaoCriadaMensagem(7L, Perfil.RESPONSAVEL, 12L));

        verify(websocket, never()).convertAndSendToUser(anyString(), anyString(), any());
        assertThat(enviadas()).isZero();
    }

    @Test
    void avisoForaDoContratoEIgnorado() {
        entregador.entregar(new NotificacaoCriadaMensagem(null, Perfil.RESPONSAVEL, 12L));
        entregador.entregar(new NotificacaoCriadaMensagem(7L, null, 12L));

        verifyNoInteractions(sessoes, websocket);
        verify(notificacaoService, never()).buscarPorId(any(), anyLong());
    }
}
