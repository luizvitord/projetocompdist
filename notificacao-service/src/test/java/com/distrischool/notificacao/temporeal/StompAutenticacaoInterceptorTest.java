package com.distrischool.notificacao.temporeal;

import com.distrischool.notificacao.notificacao.Destinatario;
import com.distrischool.notificacao.notificacao.Perfil;
import com.distrischool.notificacao.seguranca.DestinatarioJwt;
import com.distrischool.notificacao.seguranca.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Autenticação do frame CONNECT e regras de SUBSCRIBE/SEND, com o decoder real do serviço. */
class StompAutenticacaoInterceptorTest {

    private final StompAutenticacaoInterceptor interceptor = new StompAutenticacaoInterceptor(decoder());
    private final MessageChannel canal = mock(MessageChannel.class);

    /** O mesmo decoder de SecurityConfig. */
    private static NimbusJwtDecoder decoder() {
        var chave = new SecretKeySpec(TestTokens.SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), DestinatarioJwt.perfilValido()));
        return decoder;
    }

    private static Message<byte[]> frame(StompCommand comando, String authorization, String destino) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(comando);
        accessor.setSessionId("sessao-1");
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (destino != null) {
            accessor.setDestination(destino);
        }
        // Mutável, como as mensagens que chegam do cliente
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static java.security.Principal usuario(Message<?> mensagem) {
        return MessageHeaderAccessor.getAccessor(mensagem, StompHeaderAccessor.class).getUser();
    }

    @Test
    void connectComTokenValidoDefineODestinatarioComoUsuarioDaSessao() {
        Message<?> conectado = interceptor.preSend(
                frame(StompCommand.CONNECT, TestTokens.bearer("RESPONSAVEL", 12L), null), canal);

        assertThat(usuario(conectado)).isEqualTo(new DestinatarioPrincipal(Destinatario.de(Perfil.RESPONSAVEL, 12L)));
        assertThat(usuario(conectado).getName()).isEqualTo("RESPONSAVEL:12");
    }

    @Test
    void nomesDosUsuariosSeguemPerfilEReferencia() {
        assertThat(usuario(interceptor.preSend(frame(StompCommand.CONNECT, TestTokens.bearer("ALUNO", 12L), null), canal))
                .getName()).isEqualTo("ALUNO:12");
        assertThat(usuario(interceptor.preSend(frame(StompCommand.CONNECT, TestTokens.bearer("PROFESSOR", 7L), null), canal))
                .getName()).isEqualTo("PROFESSOR:7");
        // Todos os administradores são a coordenação, mesmo com referência no token
        assertThat(usuario(interceptor.preSend(frame(StompCommand.CONNECT, TestTokens.bearer("ADMIN", 3L), null), canal))
                .getName()).isEqualTo("ADMIN");
    }

    @Test
    void connectSemTokenERecusado() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null), canal))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Token ausente");
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.CONNECT, TestTokens.token("ALUNO", 12L), null), canal))
                .as("sem o prefixo Bearer")
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connectComTokenInvalidoERecusado() {
        String outroSegredo = TestTokens.token("ADMIN", null, "outro-segredo-qualquer-com-mais-de-32-bytes",
                Instant.now().plus(Duration.ofHours(1)));
        String expirado = TestTokens.token("ADMIN", null, TestTokens.SEGREDO, Instant.now().minus(Duration.ofHours(1)));
        String semPerfil = TestTokens.token(null, 12L);

        for (String token : new String[] {outroSegredo, expirado, semPerfil, "nao-e-um-jwt", ""}) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer " + token, null), canal))
                    .isInstanceOf(MessageDeliveryException.class)
                    .hasMessageContaining("Token inválido ou expirado")
                    // O token não volta para o cliente nem vai para o log
                    .hasMessageNotContaining("eyJ");
        }
    }

    @Test
    void soAFilaDoProprioUsuarioPodeSerAssinada() {
        Message<?> permitido = frame(StompCommand.SUBSCRIBE, null, "/user/queue/notificacoes");
        assertThat(interceptor.preSend(permitido, canal)).isSameAs(permitido);

        for (String destino : new String[] {"/queue/notificacoes", "/queue/notificacoes-userabc123",
                "/user/ADMIN/queue/notificacoes", "/topic/qualquer"}) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, destino), canal))
                    .as(destino)
                    .isInstanceOf(MessageDeliveryException.class);
        }
    }

    @Test
    void clienteNaoEnviaMensagens() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SEND, null, "/user/ALUNO:12/queue/notificacoes"), canal))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void demaisFramesPassamSemAlteracao() {
        Message<?> desconexao = frame(StompCommand.DISCONNECT, null, null);
        assertThat(interceptor.preSend(desconexao, canal)).isSameAs(desconexao);

        // Mensagem sem cabeçalhos STOMP (ex.: heartbeat)
        Message<byte[]> simples = MessageBuilder.withPayload(new byte[0]).build();
        assertThat(interceptor.preSend(simples, canal)).isSameAs(simples);
    }
}
