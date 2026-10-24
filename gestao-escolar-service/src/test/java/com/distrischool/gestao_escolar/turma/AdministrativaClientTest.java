package com.distrischool.gestao_escolar.turma;

import com.distrischool.gestao_escolar.common.ServicoIndisponivelException;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Fallback síncrono: retry e circuit breaker com a mesma política de application.properties
 * (só falhas de conexão/timeout e 5xx contam; 404 é resposta válida).
 */
class AdministrativaClientTest {

    private static final String URL = "http://administrativa/api/turmas/12";

    private MockRestServiceServer server;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private AdministrativaClient client;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://administrativa");
        server = MockRestServiceServer.bindTo(builder).build();

        circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
        client = new AdministrativaClient(builder.build(), circuitBreakerRegistry, retryRegistry);
    }

    @Test
    void leATurmaIgnorandoCamposQueNaoSaoUsados() {
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("""
                {"id": 12, "codigo": "9A-2026", "nome": "9º Ano A", "anoLetivo": 2026, "turno": "MANHA",
                 "capacidade": 35, "professores": [{"id": 7, "nome": "Carlos Lima"}],
                 "disciplinas": [{"disciplinaId": 3, "codigo": "MAT", "nome": "Matemática",
                                  "professorId": 7, "professorNome": "Carlos Lima"}],
                 "createdAt": "2026-10-24T11:00:00Z", "updatedAt": "2026-10-24T11:00:00Z"}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.buscarTurma(12L)).contains(new TurmaAdministrativaResponse(
                12L, "9A-2026", "9º Ano A", 2026, "MANHA", 35,
                List.of(new ScheduleUpdatedMessage.DisciplinaOfertada(3L, "MAT", "Matemática", 7L))));
        server.verify();
    }

    @Test
    void turmaInexistenteVoltaVaziaSemRetryENaoContaComoFalha() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.buscarTurma(12L)).isEmpty();
        server.verify();
        assertThat(circuito().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void erro5xxERetentadoTresVezesEViraIndisponivel() {
        server.expect(times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.buscarTurma(12L))
                .isInstanceOf(ServicoIndisponivelException.class)
                .hasCauseInstanceOf(HttpServerErrorException.class);
        server.verify();
    }

    @Test
    void falhaDeConexaoERetentadaEDepoisSeRecupera() {
        server.expect(times(2), requestTo(URL)).andRespond(withException(new IOException("Connection refused")));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("""
                {"id": 12, "codigo": "9A-2026", "nome": "9º Ano A", "anoLetivo": 2026, "turno": "MANHA",
                 "capacidade": null, "disciplinas": []}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.buscarTurma(12L)).get().extracting(TurmaAdministrativaResponse::codigo).isEqualTo("9A-2026");
        server.verify();
    }

    @Test
    void circuitoAbertoRespondeIndisponivelSemChamarOServico() {
        circuito().transitionToOpenState();
        server.expect(never(), requestTo(URL));

        assertThatThrownBy(() -> client.buscarTurma(12L))
                .isInstanceOf(ServicoIndisponivelException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
        server.verify();
    }

    @Test
    void falhasSeguidasAbremOCircuito() {
        // O circuito abre na 5ª falha registrada (mínimo de chamadas para avaliá-lo): a 3ª
        // tentativa da 2ª chamada já é recusada sem chegar ao servidor
        server.expect(times(5), requestTo(URL)).andRespond(withException(new IOException("Connection refused")));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> client.buscarTurma(12L)).isInstanceOf(ServicoIndisponivelException.class);
        }

        assertThat(circuito().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> client.buscarTurma(12L))
                .hasCauseInstanceOf(CallNotPermittedException.class);
        server.verify();
    }

    @Test
    void respostaMaisLentaQueOTimeoutFalha() throws Exception {
        HttpServer lento = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        lento.createContext("/api/turmas/12", exchange -> {
            try {
                Thread.sleep(2_000);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        lento.start();
        try {
            RestClient restClient = AdministrativaClientConfig.criarRestClient(RestClient.builder(),
                    "http://127.0.0.1:" + lento.getAddress().getPort(), Duration.ofMillis(200));
            AdministrativaClient comTimeout = new AdministrativaClient(restClient, circuitBreakerRegistry,
                    RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()));

            long inicio = System.nanoTime();
            assertThatThrownBy(() -> comTimeout.buscarTurma(12L))
                    .isInstanceOf(ServicoIndisponivelException.class)
                    .hasCauseInstanceOf(ResourceAccessException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(1_500));
        } finally {
            lento.stop(0);
        }
    }

    private CircuitBreaker circuito() {
        return circuitBreakerRegistry.circuitBreaker(AdministrativaClient.INSTANCIA);
    }
}
