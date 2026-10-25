package com.distrischool.api_gateway.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayConfigTest {

    private static HttpServer escolar;
    private static HttpServer administrativa;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startUpstreams() throws IOException {
        escolar = upstream();
        escolar.createContext("/api/alunos/7/boletim", exchange -> respond(exchange, 200,
                exchange.getRequestURI().getPath() + "?" + exchange.getRequestURI().getQuery()));
        escolar.createContext("/api/matriculas/9/status", exchange -> respond(exchange, 409,
                "{\"type\":\"about:blank\",\"status\":409,\"detail\":\"Transição inválida\"}",
                "application/problem+json"));
        escolar.start();

        administrativa = upstream();
        administrativa.createContext("/api/turmas", exchange -> respond(exchange, 200,
                exchange.getRequestURI().getPath() + "?" + exchange.getRequestURI().getQuery()));
        administrativa.start();
    }

    @AfterAll
    static void stopUpstreams() {
        escolar.stop(0);
        administrativa.stop(0);
    }

    @DynamicPropertySource
    static void gatewayUrls(DynamicPropertyRegistry registry) {
        registry.add("gateway.escolar-url", () -> url(escolar));
        registry.add("gateway.administrativa-url", () -> url(administrativa));
        registry.add("gateway.allowed-origin", () -> "http://localhost:5173");
    }

    @Test
    void routesNestedBoletimPathAndQueryToEscolar() throws Exception {
        HttpResponse<String> response = request("/api/alunos/7/boletim?anoLetivo=2026", HttpRequest.Builder::GET);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).isEqualTo("/api/alunos/7/boletim?anoLetivo=2026");
    }

    @Test
    void routesTurmaPathToAdministrativa() throws Exception {
        HttpResponse<String> response = request("/api/turmas?page=1", HttpRequest.Builder::GET);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).isEqualTo("/api/turmas?page=1");
    }

    @Test
    void relaysConflictProblemDetailWithoutChangingStatus() throws Exception {
        HttpResponse<String> response = request("/api/matriculas/9/status", builder -> builder
                .method(HttpMethod.PATCH.name(), HttpRequest.BodyPublishers.ofString("{}")));

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(response.body()).contains("Transição inválida");
    }

    @Test
    void allowsConfiguredFrontendOriginAndRejectsAnother() throws Exception {
        HttpResponse<String> allowedResponse = preflight("http://localhost:5173");
        assertThat(allowedResponse.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
                .isEqualTo("http://localhost:5173");

        HttpResponse<String> rejectedResponse = preflight("http://untrusted.test");
        assertThat(rejectedResponse.headers().firstValue("Access-Control-Allow-Origin").orElse(null)).isNull();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        return request("/api/alunos/7/boletim", builder -> builder.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET"));
    }

    private HttpResponse<String> request(String path, java.util.function.Function<HttpRequest.Builder, HttpRequest.Builder> method)
            throws Exception {
        HttpRequest request = method.apply(HttpRequest.newBuilder(URI.create(url(path)))).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer upstream() throws IOException {
        return HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    }

    private static String url(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, body, "text/plain");
    }

    private static void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
