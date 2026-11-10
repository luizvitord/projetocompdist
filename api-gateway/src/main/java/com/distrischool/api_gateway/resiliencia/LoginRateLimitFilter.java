package com.distrischool.api_gateway.resiliencia;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.concurrent.TimeUnit;

/**
 * Limita {@code POST /api/auth/login} por IP de origem (token bucket do Bucket4j), contra força
 * bruta de senhas. Excedido o limite, responde 429 com ProblemDetail e {@code Retry-After}, sem
 * chegar ao usuarios-service.
 * <p>
 * O IP é o {@code remoteAddr} da requisição; com {@code server.forward-headers-strategy=native}, o
 * Tomcat o preenche a partir do {@code X-Forwarded-For} enviado pelos proxies da rede interna
 * (nginx do frontend, Ingress). Os buckets ficam em memória, por réplica do gateway; um bucket
 * sem uso por um período inteiro já estaria cheio de novo, então pode sair do cache.
 * <p>
 * O filtro Bucket4j do Spring Cloud Gateway responde 429 sem corpo e exige um ProxyManager
 * assíncrono; por isso este filtro usa o Bucket4j diretamente.
 */
public class LoginRateLimitFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    static final String LOGIN = "/api/auth/login";

    private final ResilienciaProperties.Login config;
    private final Cache<String, Bucket> buckets;

    public LoginRateLimitFilter(ResilienciaProperties.Login config) {
        this.config = config;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(config.periodo())
                .build();
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        if (!HttpMethod.POST.equals(request.method())
                || !LOGIN.equals(request.requestPath().pathWithinApplication().value())) {
            return next.handle(request);
        }
        Bucket bucket = buckets.get(request.servletRequest().getRemoteAddr(), ip -> novoBucket());
        ConsumptionProbe consumo = bucket.tryConsumeAndReturnRemaining(1);
        if (consumo.isConsumed()) {
            return next.handle(request);
        }
        long segundos = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(consumo.getNanosToWaitForRefill() + 999_999_999L));
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        return Problemas.builder(status)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(segundos))
                .body(Problemas.corpo(status, "Muitas tentativas de login",
                        "Muitas tentativas de login. Aguarde " + segundos + " segundos e tente novamente.", request));
    }

    private Bucket novoBucket() {
        return Bucket.builder()
                .addLimit(limite -> limite.capacity(config.tentativas()).refillGreedy(config.tentativas(), config.periodo()))
                .build();
    }
}
