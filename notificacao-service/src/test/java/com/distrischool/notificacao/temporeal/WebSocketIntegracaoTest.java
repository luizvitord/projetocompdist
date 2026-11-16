package com.distrischool.notificacao.temporeal;

import com.distrischool.notificacao.notificacao.Destinatario;
import com.distrischool.notificacao.notificacao.NotificacaoMetricas;
import com.distrischool.notificacao.notificacao.NotificacaoResponse;
import com.distrischool.notificacao.notificacao.NotificacaoService;
import com.distrischool.notificacao.notificacao.Perfil;
import com.distrischool.notificacao.notificacao.TipoNotificacao;
import com.distrischool.notificacao.seguranca.SecurityConfig;
import com.distrischool.notificacao.seguranca.TestTokens;
import com.distrischool.notificacao.seguranca.ValidadorSessaoUsuario;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.websocket.autoconfigure.servlet.WebSocketMessagingAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * WebSocket de verdade: servidor Tomcat em porta aleatória, com a segurança e a configuração STOMP
 * do serviço, e clientes STOMP conectando em {@code /ws}. Sem banco nem Kafka: o aviso de
 * {@code notificacao.criada} é entregue direto ao {@link EntregadorTempoReal}, e o
 * NotificacaoService é simulado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = WebSocketIntegracaoTest.Aplicacao.class)
class WebSocketIntegracaoTest {

    @SpringBootConfiguration
    @ImportAutoConfiguration({
            TomcatServletWebServerAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            WebSocketMessagingAutoConfiguration.class
    })
    @Import({SecurityConfig.class, WebSocketConfig.class, StompAutenticacaoInterceptor.class,
            EntregadorTempoReal.class, SessoesWebSocket.class, NotificacaoMetricas.class})
    static class Aplicacao {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private static final Duration ESPERA = Duration.ofSeconds(5);
    private static final ParameterizedMapa MAPA = new ParameterizedMapa();

    @LocalServerPort
    private int porta;

    @Autowired
    private EntregadorTempoReal entregador;

    @Autowired
    private MeterRegistry registry;

    @MockitoBean
    private NotificacaoService notificacaoService;

    @MockitoBean
    private ValidadorSessaoUsuario validadorSessaoUsuario;

    private final List<StompSession> sessoesAbertas = new ArrayList<>();

    @BeforeEach
    void aceitaSessaoAtual() {
        when(validadorSessaoUsuario.valida(any())).thenReturn(true);
    }

    @AfterEach
    void desconectar() {
        sessoesAbertas.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    /** Cliente conectado e inscrito em /user/queue/notificacoes, com o que ele recebeu. */
    private record Cliente(StompSession sessao, BlockingQueue<Map<String, Object>> recebidas, StompHeaders connected) {
    }

    private CompletableFuture<StompSession> conectar(String authorization, AtomicReference<StompHeaders> connected) {
        WebSocketStompClient cliente = new WebSocketStompClient(new StandardWebSocketClient());
        cliente.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        if (authorization != null) {
            // O navegador não envia cabeçalhos no handshake: o token vai no frame CONNECT
            connectHeaders.add("Authorization", authorization);
        }
        return cliente.connectAsync("ws://localhost:" + porta + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        connected.set(connectedHeaders);
                    }
                });
    }

    private Cliente conectarEAssinar(String perfil, Long referenciaId) throws Exception {
        AtomicReference<StompHeaders> connected = new AtomicReference<>();
        StompSession sessao = conectar(TestTokens.bearer(perfil, referenciaId), connected)
                .get(ESPERA.toSeconds(), TimeUnit.SECONDS);
        sessoesAbertas.add(sessao);
        BlockingQueue<Map<String, Object>> recebidas = new LinkedBlockingQueue<>();
        sessao.subscribe(StompAutenticacaoInterceptor.DESTINO_DO_USUARIO, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MAPA.getType();
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                recebidas.add((Map<String, Object>) payload);
            }
        });
        return new Cliente(sessao, recebidas, connected.get());
    }

    private static final class ParameterizedMapa extends org.springframework.core.ParameterizedTypeReference<Map<String, Object>> {
    }

    private void existe(Destinatario destinatario, long id, String mensagem) {
        when(notificacaoService.buscarPorId(destinatario, id)).thenReturn(Optional.of(new NotificacaoResponse(id,
                TipoNotificacao.NOTA_LANCADA, "Nova nota", mensagem, "/alunos/1",
                Instant.parse("2026-11-10T11:00:00Z"), null, false)));
    }

    /**
     * A assinatura é assíncrona (o broker simples não confirma o SUBSCRIBE): repete um aviso de
     * aquecimento até o cliente receber o primeiro e, depois, esvazia a fila.
     */
    private void aguardarAssinatura(Cliente cliente, Destinatario destinatario) throws InterruptedException {
        long idAquecimento = 1_000_000L + sessoesAbertas.indexOf(cliente.sessao());
        existe(destinatario, idAquecimento, "aquecimento");
        Map<String, Object> recebida = null;
        for (int tentativa = 0; tentativa < 50 && recebida == null; tentativa++) {
            entregador.entregar(new NotificacaoCriadaMensagem(idAquecimento, destinatario.perfil(),
                    destinatario.referenciaId()));
            recebida = cliente.recebidas().poll(100, TimeUnit.MILLISECONDS);
        }
        assertThat(recebida).as("assinatura de " + destinatario.nome()).isNotNull();
        // Sobras do aquecimento que ainda estejam a caminho
        while (cliente.recebidas().poll(300, TimeUnit.MILLISECONDS) != null) {
            // descarta
        }
    }

    private double sessoesAtivas() {
        return registry.get("websocket.sessoes.ativas").gauge().value();
    }

    private void aguardarSessoesAtivas(double esperado) throws InterruptedException {
        for (int tentativa = 0; tentativa < 50 && sessoesAtivas() != esperado; tentativa++) {
            Thread.sleep(100);
        }
        assertThat(sessoesAtivas()).isEqualTo(esperado);
    }

    @Test
    void conexaoSemTokenERecusada() {
        CompletableFuture<StompSession> conexao = conectar(null, new AtomicReference<>());

        assertThatThrownBy(() -> conexao.get(ESPERA.toSeconds(), TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void conexaoComTokenInvalidoERecusada() {
        String outroSegredo = TestTokens.token("ADMIN", null, "outro-segredo-qualquer-com-mais-de-32-bytes",
                Instant.now().plus(Duration.ofHours(1)));
        String expirado = TestTokens.token("ALUNO", 1L, TestTokens.SEGREDO, Instant.now().minus(Duration.ofHours(1)));

        for (String token : List.of(outroSegredo, expirado, "nao-e-um-jwt")) {
            CompletableFuture<StompSession> conexao = conectar("Bearer " + token, new AtomicReference<>());

            assertThatThrownBy(() -> conexao.get(ESPERA.toSeconds(), TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class);
        }
        assertThat(sessoesAtivas()).isZero();
    }

    @Test
    void conexaoComTokenValidoNegociaHeartbeatsDeDezSegundos() throws Exception {
        Cliente cliente = conectarEAssinar("RESPONSAVEL", 1L);

        assertThat(cliente.sessao().isConnected()).isTrue();
        assertThat(cliente.connected().getHeartbeat()).containsExactly(10_000L, 10_000L);
    }

    @Test
    void notificacaoChegaAoDestinatarioCertoENaoAosOutros() throws Exception {
        Destinatario responsavel1 = Destinatario.de(Perfil.RESPONSAVEL, 1L);
        Destinatario aluno1 = Destinatario.de(Perfil.ALUNO, 1L);
        Destinatario responsavel2 = Destinatario.de(Perfil.RESPONSAVEL, 2L);
        Cliente doResponsavel1 = conectarEAssinar("RESPONSAVEL", 1L);
        Cliente doAluno1 = conectarEAssinar("ALUNO", 1L);
        Cliente doResponsavel2 = conectarEAssinar("RESPONSAVEL", 2L);
        Cliente daCoordenacao = conectarEAssinar("ADMIN", null);
        aguardarAssinatura(doResponsavel1, responsavel1);
        aguardarAssinatura(doAluno1, aluno1);
        aguardarAssinatura(doResponsavel2, responsavel2);
        aguardarAssinatura(daCoordenacao, Destinatario.admin());

        existe(responsavel1, 7L, "Nova nota em Matemática (1º bimestre): 8,5");
        entregador.entregar(new NotificacaoCriadaMensagem(7L, Perfil.RESPONSAVEL, 1L));

        Map<String, Object> recebida = doResponsavel1.recebidas().poll(ESPERA.toSeconds(), TimeUnit.SECONDS);
        assertThat(recebida).isNotNull();
        assertThat(recebida).containsEntry("id", 7)
                .containsEntry("tipo", "NOTA_LANCADA")
                .containsEntry("titulo", "Nova nota")
                .containsEntry("mensagem", "Nova nota em Matemática (1º bimestre): 8,5")
                .containsEntry("link", "/alunos/1")
                .containsEntry("lida", false);
        // Mesmo aluno com outro perfil, outro responsável e a coordenação não recebem
        assertThat(doAluno1.recebidas().poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(doResponsavel2.recebidas()).isEmpty();
        assertThat(daCoordenacao.recebidas()).isEmpty();
        assertThat(doResponsavel1.recebidas()).isEmpty();
    }

    @Test
    void todasAsSessoesDoMesmoDestinatarioRecebem() throws Exception {
        // Dois administradores (ou duas abas): o mesmo destinatário ADMIN
        Cliente coordenacao1 = conectarEAssinar("ADMIN", null);
        Cliente coordenacao2 = conectarEAssinar("ADMIN", 3L);
        aguardarAssinatura(coordenacao1, Destinatario.admin());
        while (coordenacao2.recebidas().poll(300, TimeUnit.MILLISECONDS) != null) {
            // o aquecimento do primeiro também chegou a este
        }
        aguardarAssinatura(coordenacao2, Destinatario.admin());
        while (coordenacao1.recebidas().poll(300, TimeUnit.MILLISECONDS) != null) {
            // idem
        }

        existe(Destinatario.admin(), 9L, "Aluno cadastrado: Ana Souza (matrícula 2026000001)");
        entregador.entregar(new NotificacaoCriadaMensagem(9L, Perfil.ADMIN, null));

        assertThat(coordenacao1.recebidas().poll(ESPERA.toSeconds(), TimeUnit.SECONDS)).containsEntry("id", 9);
        assertThat(coordenacao2.recebidas().poll(ESPERA.toSeconds(), TimeUnit.SECONDS)).containsEntry("id", 9);
    }

    @Test
    void sessoesAtivasAcompanhamConexoesEDesconexoes() throws Exception {
        when(notificacaoService.buscarPorId(any(), anyLong())).thenReturn(Optional.empty());
        aguardarSessoesAtivas(0);

        Cliente primeiro = conectarEAssinar("ALUNO", 1L);
        Cliente segundo = conectarEAssinar("PROFESSOR", 7L);
        aguardarSessoesAtivas(2);

        primeiro.sessao().disconnect();
        aguardarSessoesAtivas(1);
        segundo.sessao().disconnect();
        aguardarSessoesAtivas(0);
    }
}
