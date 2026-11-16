package com.distrischool.notificacao.temporeal;

import com.distrischool.notificacao.seguranca.DestinatarioJwt;
import com.distrischool.notificacao.seguranca.ValidadorSessaoUsuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Autentica as sessões STOMP. O navegador não consegue enviar cabeçalhos HTTP no handshake do
 * WebSocket, então o token vai no frame {@code CONNECT} ({@code Authorization: Bearer <token>}) e é
 * validado aqui com o mesmo {@link JwtDecoder} da API REST (assinatura, expiração e perfil), além
 * do estado atual da sessão no usuários-service.
 * <p>
 * Uma exceção lançada daqui faz o Spring responder com um frame {@code ERROR} e fechar a sessão:
 * sem token válido não há conexão. Com token válido, o usuário da sessão passa a ser o
 * destinatário ({@link DestinatarioPrincipal}).
 * <p>
 * Depois de conectado, o cliente só pode assinar a própria fila de notificações; o serviço não
 * recebe mensagens de aplicação ({@code SEND}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAutenticacaoInterceptor implements ChannelInterceptor {

    /** Destino que o cliente assina; o Spring o traduz para uma fila exclusiva de cada sessão. */
    public static final String DESTINO_DO_USUARIO = "/user/queue/notificacoes";

    private static final String BEARER = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final ValidadorSessaoUsuario validadorSessaoUsuario;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // getAccessor (e não wrap): o usuário precisa ser gravado nos cabeçalhos da própria mensagem
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> accessor.setUser(autenticar(accessor));
            case SUBSCRIBE -> {
                if (!DESTINO_DO_USUARIO.equals(accessor.getDestination())) {
                    throw new MessageDeliveryException("Destino não permitido");
                }
            }
            case SEND -> throw new MessageDeliveryException("Este serviço não recebe mensagens");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK...: nada a validar
            }
        }
        return message;
    }

    private DestinatarioPrincipal autenticar(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER)) {
            throw new MessageDeliveryException("Token ausente");
        }
        try {
            Jwt jwt = jwtDecoder.decode(authorization.substring(BEARER.length()).trim());
            if (!validadorSessaoUsuario.valida(jwt)) {
                throw new MessageDeliveryException("Token inválido ou expirado");
            }
            DestinatarioPrincipal principal = new DestinatarioPrincipal(DestinatarioJwt.de(jwt));
            log.debug("Sessão WebSocket {} autenticada como {}", accessor.getSessionId(), principal.getName());
            return principal;
        } catch (JwtException ex) {
            // Sem a causa: a mensagem do decoder não acrescenta nada ao cliente
            throw new MessageDeliveryException("Token inválido ou expirado");
        }
    }
}
