package com.distrischool.api_gateway.resiliencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.Set;

/**
 * Novas tentativas, com espera exponencial, só para GET e só quando a requisição com certeza não
 * foi processada ou o serviço sinalizou indisponibilidade temporária:
 * <ul>
 *   <li>erro de conexão (recusada, timeout de conexão, nome não resolvido);</li>
 *   <li>respostas 502, 503 e 504.</li>
 * </ul>
 * Timeout de leitura e 500 não são repetidos: o serviço pode ter recebido a requisição. POST, PUT,
 * PATCH e DELETE nunca são reenviados, porque não são idempotentes (ou podem não ser).
 * <p>
 * O filtro de retry do Spring Cloud Gateway não serve aqui: ele filtra por faixa de status (todo
 * 5xx) e repete qualquer {@code IOException}, inclusive timeout de leitura.
 */
public class RetryGetFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private static final Logger log = LoggerFactory.getLogger(RetryGetFilter.class);
    private static final Set<Integer> STATUS_REPETIVEIS = Set.of(502, 503, 504);

    private final int novasTentativas;
    private final Duration esperaInicial;

    public RetryGetFilter(ResilienciaProperties.Retry config) {
        this.novasTentativas = config.novasTentativas();
        this.esperaInicial = config.esperaInicial();
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        if (!HttpMethod.GET.equals(request.method())) {
            return next.handle(request);
        }
        for (int tentativa = 0; ; tentativa++) {
            boolean ultima = tentativa >= novasTentativas;
            try {
                ServerResponse resposta = next.handle(request);
                if (ultima || !STATUS_REPETIVEIS.contains(resposta.statusCode().value())) {
                    return resposta;
                }
                log.debug("GET {} respondeu {}; nova tentativa", caminho(request), resposta.statusCode().value());
                descartarResposta(request);
            } catch (ResourceAccessException ex) {
                if (ultima || !erroDeConexao(ex)) {
                    throw ex;
                }
                log.debug("GET {} sem conexão ({}); nova tentativa", caminho(request), ex.getMessage());
            }
            Thread.sleep(esperaInicial.multipliedBy(1L << tentativa));
        }
    }

    /** Fecha a resposta descartada do serviço de trás, como o filtro de retry do gateway faz. */
    private static void descartarResposta(ServerRequest request) {
        ClientHttpResponse resposta = MvcUtils.getAttribute(request, MvcUtils.CLIENT_RESPONSE_ATTR);
        if (resposta != null) {
            resposta.close();
            MvcUtils.putAttribute(request, MvcUtils.CLIENT_RESPONSE_ATTR, null);
        }
    }

    static boolean erroDeConexao(Throwable erro) {
        for (Throwable t = erro; t != null; t = t.getCause() != t ? t.getCause() : null) {
            if (t instanceof ConnectException || t instanceof HttpConnectTimeoutException
                    || t instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }

    private static String caminho(ServerRequest request) {
        return request.requestPath().pathWithinApplication().value();
    }
}
