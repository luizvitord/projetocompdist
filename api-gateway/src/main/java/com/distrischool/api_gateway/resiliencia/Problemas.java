package com.distrischool.api_gateway.resiliencia;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

/**
 * Respostas de erro geradas pelo próprio gateway, no mesmo formato RFC 9457 (ProblemDetail) dos
 * serviços de trás: o frontend lê {@code status} e {@code detail} (ver problemDetail.ts).
 */
final class Problemas {

    private Problemas() {
    }

    static ServerResponse.BodyBuilder builder(HttpStatus status) {
        return ServerResponse.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    static ProblemDetail corpo(HttpStatus status, String titulo, String detalhe, ServerRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        problema.setInstance(URI.create(request.requestPath().pathWithinApplication().value()));
        return problema;
    }
}
