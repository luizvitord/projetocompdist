package com.distrischool.gestao_escolar.turma;

import com.distrischool.gestao_escolar.common.ServicoIndisponivelException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Fallback síncrono para turmas que ainda não chegaram à réplica: consulta
 * {@code GET /api/turmas/{id}} no gestao-administrativa-service, com retry e circuit breaker
 * (instância {@value #INSTANCIA} do Resilience4j, configurada em application.properties).
 */
@Component
public class AdministrativaClient {

    static final String INSTANCIA = "administrativa";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public AdministrativaClient(@Qualifier("administrativaRestClient") RestClient restClient,
                                CircuitBreakerRegistry circuitBreakerRegistry,
                                RetryRegistry retryRegistry) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCIA);
        this.retry = retryRegistry.retry(INSTANCIA);
    }

    /**
     * @return a turma, ou vazio se ela não existir no outro serviço (404)
     * @throws ServicoIndisponivelException se o serviço estiver fora do ar, responder com erro
     *         depois das novas tentativas ou estiver com o circuito aberto
     */
    public Optional<TurmaAdministrativaResponse> buscarTurma(Long turmaId) {
        // O retry envolve o circuit breaker: cada tentativa conta para o circuito, e com o
        // circuito aberto não há novas tentativas (CallNotPermittedException não é retentada)
        Supplier<Optional<TurmaAdministrativaResponse>> chamada = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> get(turmaId)));
        try {
            return chamada.get();
        } catch (CallNotPermittedException ex) {
            throw new ServicoIndisponivelException(
                    "gestao-administrativa-service indisponível; tente novamente em instantes", ex);
        } catch (RestClientException ex) {
            throw new ServicoIndisponivelException(
                    "Não foi possível consultar a turma " + turmaId + " no gestao-administrativa-service", ex);
        }
    }

    private Optional<TurmaAdministrativaResponse> get(Long turmaId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/turmas/{id}", turmaId)
                    .retrieve()
                    .body(TurmaAdministrativaResponse.class));
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        }
    }
}
