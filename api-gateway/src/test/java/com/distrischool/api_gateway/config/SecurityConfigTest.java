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
        registry.add("gateway.notificacao-url", () -> url);
    }

    @Test
    void loginAndHealthDoNotNeedToken() throws Exception {
        assertThat(send("POST", "/api/auth/login", null)).isEqualTo(200);
        assertThat(send("GET", "/actuator/health/liveness", null)).isEqualTo(200);
    }

    /** Prometheus scrapes the gateway without a token; a missing registry used to turn this into 401. */
    @Test
    void prometheusEndpointIsOpenAndExposesHttpHistogramBuckets() throws Exception {
        assertThat(send("GET", "/api/turmas", TestTokens.bearer("ADMIN"))).isEqualTo(200);

        HttpResponse<String> metrics = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/prometheus")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("http_server_requests_seconds_bucket");
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

    /** Regression: ADMIN and PROFESSOR still read everything (ALUNO/RESPONSAVEL in AcessoDoAlunoTest). */
    @Test
    void adminAndProfessorReadEverything() throws Exception {
        String[] paths = {
                "/api/alunos?size=5", "/api/alunos/7", "/api/alunos/7/boletim?anoLetivo=2026",
                "/api/frequencias/alunos/7?anoLetivo=2026", "/api/matriculas", "/api/matriculas?turmaId=3",
                "/api/turmas", "/api/turmas/3", "/api/professores", "/api/tecnicos", "/api/disciplinas",
                "/api/avaliacoes", "/api/avaliacoes/5/notas", "/api/notas/9/historico",
                "/api/frequencias/chamadas", "/api/frequencias/relatorio?turmaId=3"};
        for (String perfil : new String[] {"ADMIN", "PROFESSOR"}) {
            for (String path : paths) {
                assertThat(send("GET", path, TestTokens.bearer(perfil, 3L))).as(perfil + " " + path).isEqualTo(200);
                assertThat(send("GET", path, TestTokens.bearer(perfil))).as(perfil + " without referenciaId " + path)
                        .isEqualTo(200);
            }
        }
    }

    /** Every 403 is a ProblemDetail and keeps the resource server's WWW-Authenticate header. */
    @Test
    void forbiddenIsAProblemDetail() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/turmas"))
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("PROFESSOR"))
                        .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE)).hasValue("application/problem+json");
        assertThat(response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE)).hasValueSatisfying(
                valor -> assertThat(valor).startsWith("Bearer"));
        assertThat(response.body()).isEqualTo("{\"type\":\"about:blank\",\"title\":\"Acesso negado\",\"status\":403,"
                + "\"detail\":\"O seu perfil não tem permissão para acessar este recurso.\",\"instance\":\"/api/turmas\"}");
    }

    /**
     * Paths rejected by the Spring Security firewall answer 403 for every profile (they used to end
     * as a 401 from /error, or a 400 from Tomcat for %2F) and never reach a route.
     */
    @Test
    void suspiciousPathsAreForbiddenForEveryProfile() throws Exception {
        for (String path : new String[] {"/api/alunos/12/../13", "/api/alunos//12", "/api/alunos/12%2F13",
                "/api/alunos/%2E%2E/13", "/api/alunos/12;x=1"}) {
            assertThat(send("GET", path, TestTokens.bearer("ADMIN"))).as(path).isEqualTo(403);
        }
    }

    @Test
    void tokenWithUnknownProfileCannotRead() throws Exception {
        assertThat(send("GET", "/api/turmas", TestTokens.bearer("VISITANTE", 7L))).isEqualTo(403);
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

    @Test
    void everyProfileReadsAndMarksItsOwnNotifications() throws Exception {
        for (String perfil : new String[] {"ADMIN", "PROFESSOR", "ALUNO", "RESPONSAVEL"}) {
            String token = TestTokens.bearer(perfil);

            assertThat(send("GET", "/api/notificacoes", token)).as(perfil).isEqualTo(200);
            assertThat(send("GET", "/api/notificacoes/nao-lidas/contagem", token)).as(perfil).isEqualTo(200);
            // Not GET: these would fall into the ADMIN-only rule if it were declared first
            assertThat(send("PATCH", "/api/notificacoes/7/lida", token)).as(perfil).isEqualTo(200);
            assertThat(send("POST", "/api/notificacoes/lidas", token)).as(perfil).isEqualTo(200);
        }
    }

    @Test
    void notificationsStillNeedAToken() throws Exception {
        assertThat(send("GET", "/api/notificacoes", null)).isEqualTo(401);
        assertThat(send("POST", "/api/notificacoes/lidas", null)).isEqualTo(401);
        // Swagger of the service is open, like the others
        assertThat(send("GET", "/notificacao/swagger-ui.html", null)).isEqualTo(200);
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
