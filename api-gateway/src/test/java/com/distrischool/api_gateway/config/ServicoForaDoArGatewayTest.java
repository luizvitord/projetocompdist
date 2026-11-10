package com.distrischool.api_gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Every upstream points to a closed port: the gateway stays up and answers 503 ProblemDetail. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gateway.resiliencia.retry.espera-inicial=10ms")
class ServicoForaDoArGatewayTest {

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void gatewayUrls(DynamicPropertyRegistry registry) {
        String closed = "http://localhost:" + closedPort();
        registry.add("gateway.escolar-url", () -> closed);
        registry.add("gateway.administrativa-url", () -> closed);
        registry.add("gateway.usuarios-url", () -> closed);
    }

    @Test
    void connectionRefusedBecomes503ProblemDetail() throws Exception {
        HttpResponse<String> response = send("GET", "/api/alunos");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        JsonNode problem = JsonMapper.builder().build().readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(503);
        assertThat(problem.get("detail").asString()).contains("temporariamente indisponível");
    }

    @Test
    void postToAServiceThatIsDownAlsoBecomes503() throws Exception {
        assertThat(send("POST", "/api/turmas").statusCode()).isEqualTo(503);
    }

    @Test
    void readinessDoesNotDependOnTheServicesBehindTheGateway() throws Exception {
        assertThat(send("GET", "/actuator/health/readiness").statusCode()).isEqualTo(200);
        assertThat(send("GET", "/actuator/health/liveness").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.ofString("{}"))
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("ADMIN"))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
