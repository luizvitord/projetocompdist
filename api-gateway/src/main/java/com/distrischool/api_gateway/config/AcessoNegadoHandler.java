package com.distrischool.api_gateway.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 403 no mesmo formato RFC 9457 (ProblemDetail) dos outros erros do gateway (503, 429) e dos
 * serviços de trás. Sem este handler, o Spring Security responde 403 sem corpo.
 * <p>
 * Dois casos:
 * <ul>
 *   <li>perfil sem permissão para a rota (AccessDeniedHandler). O cabeçalho
 *       {@code WWW-Authenticate} continua o do resource server
 *       ({@link BearerTokenAccessDeniedHandler});</li>
 *   <li>caminho recusado pelo firewall do Spring Security antes de qualquer regra
 *       ({@code ..}, {@code //}, {@code ;}, {@code %2F}, {@code %2E}...). O padrão seria um 400
 *       que o Boot encaminha para {@code /error}, e o {@code /error} sem token vira 401: o cliente
 *       via um 401 com um token válido.</li>
 * </ul>
 */
final class AcessoNegadoHandler implements AccessDeniedHandler, RequestRejectedHandler {

    static final String TITULO = "Acesso negado";
    static final String DETALHE = "O seu perfil não tem permissão para acessar este recurso.";
    static final String DETALHE_RECUSADA = "O caminho da requisição não é permitido.";

    private final BearerTokenAccessDeniedHandler cabecalho = new BearerTokenAccessDeniedHandler();
    private final JsonMapper json;

    AcessoNegadoHandler(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException negado) throws IOException {
        cabecalho.handle(request, response, negado);
        escrever(request, response, DETALHE);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       RequestRejectedException recusada) throws IOException {
        escrever(request, response, DETALHE_RECUSADA);
    }

    private void escrever(HttpServletRequest request, HttpServletResponse response, String detalhe) throws IOException {
        HttpStatus status = HttpStatus.FORBIDDEN;
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("type", "about:blank");
        corpo.put("title", TITULO);
        corpo.put("status", status.value());
        corpo.put("detail", detalhe);
        corpo.put("instance", request.getRequestURI().substring(request.getContextPath().length()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Bytes em UTF-8 (padrão do JSON), sem "charset" no Content-Type, como os outros erros
        response.getOutputStream().write(json.writeValueAsBytes(corpo));
    }
}
