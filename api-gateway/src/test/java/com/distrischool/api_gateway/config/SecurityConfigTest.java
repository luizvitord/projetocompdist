package com.distrischool.api_gateway.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Every route points to one upstream that answers 200, so the status shows what the gateway decided. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigTest {

    private static HttpServer upstream;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startUpstream() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        upstream.createContext("/", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
    }

    @AfterAll
    static void stopUpstream() {
        upstream.stop(0);
    }

    @DynamicPropertySource
    static void gatewayUrls(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + upstream.getAddress().getPort();
        registry.add("gateway.escolar-url", () -> url);
        registry.add("gateway.administrativa-url", () -> url);
        registry.add("gateway.usuarios-url", () -> url);
    }

    @Test
    void loginAndHealthDoNotNeedToken() throws Exception {
        assertThat(send("POST", "/api/auth/login", null)).isEqualTo(200);
        assertThat(send("GET", "/actuator/health/liveness", null)).isEqualTo(200);
    }

    @Test
    void rejectsRequestsWithoutValidToken() throws Exception {
        assertThat(send("GET", "/api/alunos", null)).isEqualTo(401);

        String otherSecret = TestTokens.token("ADMIN", "outro-segredo-qualquer-com-mais-de-32-bytes",
                Instant.now().plus(Duration.ofHours(1)));
        assertThat(send("GET", "/api/alunos", "Bearer " + otherSecret)).isEqualTo(401);

        String expired = TestTokens.token("ADMIN", TestTokens.SECRET, Instant.now().minus(Duration.ofHours(1)));
        assertThat(send("GET", "/api/alunos", "Bearer " + expired)).isEqualTo(401);
    }

    @Test
    void anyProfileCanRead() throws Exception {
        assertThat(send("GET", "/api/alunos/7/boletim", TestTokens.bearer("ALUNO"))).isEqualTo(200);
        assertThat(send("GET", "/api/turmas", TestTokens.bearer("RESPONSAVEL"))).isEqualTo(200);
    }

    @Test
    void professorPostsGradesAndAttendanceButNotRegistrations() throws Exception {
        String professor = TestTokens.bearer("PROFESSOR");

        assertThat(send("POST", "/api/notas", professor)).isEqualTo(200);
        assertThat(send("POST", "/api/frequencias/chamadas", professor)).isEqualTo(200);
        assertThat(send("POST", "/api/turmas", professor)).isEqualTo(403);
        assertThat(send("DELETE", "/api/alunos/7", professor)).isEqualTo(403);
    }

    @Test
    void studentCannotChangeAnything() throws Exception {
        assertThat(send("POST", "/api/notas", TestTokens.bearer("ALUNO"))).isEqualTo(403);
        assertThat(send("PATCH", "/api/matriculas/4/status", TestTokens.bearer("ALUNO"))).isEqualTo(403);
    }

    @Test
    void onlyAdminManagesUsers() throws Exception {
        assertThat(send("GET", "/api/usuarios", TestTokens.bearer("ADMIN"))).isEqualTo(200);
        assertThat(send("POST", "/api/turmas", TestTokens.bearer("ADMIN"))).isEqualTo(200);
        assertThat(send("GET", "/api/usuarios", TestTokens.bearer("PROFESSOR"))).isEqualTo(403);
    }

    private int send(String method, String path, String authorization) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.ofString("{}"));
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
