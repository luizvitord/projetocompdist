package com.distrischool.api_gateway.resiliencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Traduz a indisponibilidade de um serviço de trás em 503 com ProblemDetail.
 * <p>
 * Cobre o que chega até aqui como exceção: erro de conexão ou timeout do cliente HTTP
 * ({@link ResourceAccessException}) e o {@link ResponseStatusException} que o filtro de circuit
 * breaker lança, sem fallback, quando o circuito está aberto ou a chamada falhou. Uma resposta
 * 5xx do próprio serviço não é exceção e passa sem alteração, com o corpo original.
 */
public class IndisponibilidadeFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private static final Logger log = LoggerFactory.getLogger(IndisponibilidadeFilter.class);

    private final String servico;

    public IndisponibilidadeFilter(String servico) {
        this.servico = servico;
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        try {
            return next.handle(request);
        } catch (ResponseStatusException ex) {
            if (!ex.getStatusCode().is5xxServerError()) {
                throw ex;
            }
            return indisponivel(request, ex.getCause() != null ? ex.getCause() : ex);
        } catch (ResourceAccessException ex) {
            return indisponivel(request, ex);
        }
    }

    private ServerResponse indisponivel(ServerRequest request, Throwable causa) {
        log.warn("Serviço {} indisponível para {} {}: {}", servico, request.method(),
                request.requestPath().pathWithinApplication().value(), causa.toString());
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return Problemas.builder(status).body(Problemas.corpo(status, "Serviço indisponível",
                "O serviço está temporariamente indisponível. Tente novamente em instantes.", request));
    }
}
