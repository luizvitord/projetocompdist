package com.distrischool.api_gateway.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway against real HTTP upstreams: retry only on GET, timeout and open circuit as 503
 * ProblemDetail, and login rate limiting. Each concern uses a different route, so the circuit
 * breaker state of one test does not leak into another.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.http.clients.read-timeout=500ms",
        "resilience4j.circuitbreaker.configs.default.sliding-window-size=2",
        "resilience4j.circuitbreaker.configs.default.minimum-number-of-calls=2",
        "resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=1h",
        "gateway.resiliencia.retry.espera-inicial=10ms",
        "gateway.resiliencia.login.tentativas=3",
        "gateway.resiliencia.login.periodo=1h"
})
class ResilienciaGatewayTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, AtomicInteger> HITS = new ConcurrentHashMap<>();

    private static HttpServer escolar;
    private static HttpServer administrativa;
    private static HttpServer usuarios;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startUpstreams() throws IOException {
        escolar = upstream();
        escolar.createContext("/api/alunos/instavel", exchange -> respond(exchange, 503,
                "{\"status\":503,\"detail\":\"Gestão administrativa indisponível\"}"));
        escolar.createContext("/api/alunos/erro", exchange -> respond(exchange, 500, "{}"));
        escolar.start();

        administrativa = upstream();
        // Each slow call holds its own thread; the default executor would queue the second one
        administrativa.setExecutor(Executors.newCachedThreadPool());
        administrativa.createContext("/api/turmas/lenta", exchange -> {
            count(exchange);
            try {
                Thread.sleep(1_500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reply(exchange, 200, "{}");
        });
        administrativa.start();

        usuarios = upstream();
        usuarios.createContext("/", exchange -> respond(exchange, 200, "{}"));
        usuarios.start();
    }

    @AfterAll
    static void stopUpstreams() {
        escolar.stop(0);
        administrativa.stop(0);
        usuarios.stop(0);
    }

    @DynamicPropertySource
    static void gatewayUrls(DynamicPropertyRegistry registry) {
        registry.add("gateway.escolar-url", () -> url(escolar));
        registry.add("gateway.administrativa-url", () -> url(administrativa));
        registry.add("gateway.usuarios-url", () -> url(usuarios));
    }

    @BeforeEach
    void resetHits() {
        HITS.clear();
    }

    @Test
    void retriesGetOnServiceUnavailableAndRelaysTheLastResponse() throws Exception {
        HttpResponse<String> response = send("GET", "/api/alunos/instavel");

        assertThat(hits("/api/alunos/instavel")).isEqualTo(3);
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("Gestão administrativa indisponível");
    }

    @Test
    void neverResendsPostPutPatchOrDelete() throws Exception {
        for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
            HITS.clear();

            assertThat(send(method, "/api/alunos/instavel").statusCode()).isEqualTo(503);
            assertThat(hits("/api/alunos/instavel")).as(method).isEqualTo(1);
        }
    }

    @Test
    void doesNotRetryInternalServerError() throws Exception {
        assertThat(send("GET", "/api/alunos/erro").statusCode()).isEqualTo(500);
        assertThat(hits("/api/alunos/erro")).isEqualTo(1);
    }

    @Test
    void readTimeoutAndOpenCircuitAnswer503ProblemDetail() throws Exception {
        // Two timeouts fill the window (minimum-number-of-calls=2) and open the circuit
        for (int i = 0; i < 2; i++) {
            assertProblem(send("GET", "/api/turmas/lenta"), 503);
        }
        assertThat(hits("/api/turmas/lenta")).isEqualTo(2);

        long start = System.nanoTime();
        HttpResponse<String> response = send("GET", "/api/turmas/lenta");
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        JsonNode problem = assertProblem(response, 503);
        assertThat(problem.get("detail").asString()).contains("temporariamente indisponível");
        // Open circuit: answered by the gateway itself, without calling the service
        assertThat(hits("/api/turmas/lenta")).isEqualTo(2);
        assertThat(elapsedMillis).isLessThan(500);
    }

    @Test
    void limitsLoginAttemptsPerIpWith429ProblemDetail() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(send("POST", "/api/auth/login").statusCode()).isEqualTo(200);
        }

        HttpResponse<String> blocked = send("POST", "/api/auth/login");

        JsonNode problem = assertProblem(blocked, 429);
        assertThat(problem.get("detail").asString()).contains("Muitas tentativas");
        assertThat(blocked.headers().firstValue(HttpHeaders.RETRY_AFTER)).hasValueSatisfying(
                value -> assertThat(Long.parseLong(value)).isPositive());
        assertThat(hits("/api/auth/login")).isEqualTo(3);
        // Only the login is limited
        assertThat(send("GET", "/api/usuarios").statusCode()).isEqualTo(200);
    }

    private static JsonNode assertProblem(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        JsonNode problem = JSON.readTree(response.body());
        // Fields read by frontend/src/services/problemDetail.ts
        assertThat(problem.get("status").asInt()).isEqualTo(status);
        assertThat(problem.get("detail").asString()).isNotBlank();
        assertThat(problem.get("title").asString()).isNotBlank();
        return problem;
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.ofString("{}"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("ADMIN"))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int hits(String path) {
        return HITS.getOrDefault(path, new AtomicInteger()).get();
    }

    private static HttpServer upstream() throws IOException {
        return HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    }

    private static String url(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        count(exchange);
        reply(exchange, status, body);
    }

    /** Counts the call by path as soon as it arrives. */
    private static void count(HttpExchange exchange) {
        HITS.computeIfAbsent(exchange.getRequestURI().getPath(), p -> new AtomicInteger()).incrementAndGet();
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", status >= 400 ? "application/problem+json" : "application/json");
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // The gateway already gave up on this call (read timeout)
        } finally {
            exchange.close();
        }
    }
}
