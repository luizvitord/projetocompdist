package com.distrischool.api_gateway.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ALUNO and RESPONSAVEL only read the data of the student linked to the account (claim
 * {@code referenciaId}, here 12). Every route points to one upstream that answers 200, so the
 * status shows what the gateway decided.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcessoDoAlunoTest {

    private static final long PROPRIO = 12;
    private static final long OUTRO = 13;

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

    @ParameterizedTest
    @ValueSource(strings = {"ALUNO", "RESPONSAVEL"})
    void readsTheLinkedStudentsData(String perfil) throws Exception {
        String token = TestTokens.bearer(perfil, PROPRIO);

        assertThat(status("/api/alunos/12", token)).isEqualTo(200);
        assertThat(status("/api/alunos/12/boletim?anoLetivo=2026", token)).isEqualTo(200);
        assertThat(status("/api/frequencias/alunos/12?anoLetivo=2026", token)).isEqualTo(200);
        assertThat(status("/api/matriculas?alunoId=12", token)).isEqualTo(200);
        assertThat(status("/api/matriculas?alunoId=12&anoLetivo=2026&status=ATIVA", token)).isEqualTo(200);
        assertThat(status("/api/matriculas?anoLetivo=2026&alunoId=12&page=0&size=50&sort=id", token)).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALUNO", "RESPONSAVEL"})
    void cannotReadAnotherStudent(String perfil) throws Exception {
        String token = TestTokens.bearer(perfil, PROPRIO);

        assertForbidden("/api/alunos/13", token);
        assertForbidden("/api/alunos/13/boletim?anoLetivo=2026", token);
        assertForbidden("/api/frequencias/alunos/13?anoLetivo=2026", token);
        assertForbidden("/api/matriculas?alunoId=13", token);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALUNO", "RESPONSAVEL"})
    void cannotListOrReadOtherResources(String perfil) throws Exception {
        String token = TestTokens.bearer(perfil, PROPRIO);
        String[] paths = {
                "/api/alunos", "/api/alunos?size=5", "/api/alunos?turmaId=3", "/api/alunos?matricula=2026000012",
                "/api/turmas", "/api/turmas/3", "/api/turmas/3/alunos",
                "/api/professores", "/api/professores/1", "/api/tecnicos", "/api/tecnicos/1",
                "/api/disciplinas", "/api/disciplinas/1",
                "/api/avaliacoes", "/api/avaliacoes/5", "/api/avaliacoes/5/notas", "/api/notas/9/historico",
                "/api/frequencias/chamadas", "/api/frequencias/chamadas/4", "/api/frequencias/relatorio?turmaId=3",
                "/api/matriculas", "/api/matriculas?size=5", "/api/matriculas?turmaId=3", "/api/matriculas/4",
                "/api/usuarios", "/api/usuarios/1"};
        for (String path : paths) {
            assertThat(status(path, token)).as(path).isEqualTo(403);
        }
    }

    /** Variations of the student's own path or parameters that must not be read as id 12. */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/alunos/12abc", "/api/alunos/012", "/api/alunos/+12", "/api/alunos/12.0", "/api/alunos/%31%32",
            "/api/alunos/12/", "/api/alunos/12/boletim/", "/api/frequencias/alunos/12/",
            "/api/alunos/12/../13", "/api/alunos/13/../12", "/api/alunos/12/%2E%2E/13", "/api/alunos/12%2F..%2F13",
            "/api/alunos//12", "/api/alunos/12;x=1", "/api/alunos/12/boletim%2F", "/api/alunos/99999999999999999999",
            "/api/matriculas?alunoId=12&alunoId=13", "/api/matriculas?alunoId=12&alunoId=12",
            "/api/matriculas?alunoId=", "/api/matriculas?alunoId=012", "/api/matriculas?alunoId=12abc",
            "/api/matriculas?alunoid=12", "/api/matriculas?alunoId=12&turmaId=3", "/api/matriculas?anoLetivo=2026",
            "/api/matriculas/?alunoId=12"})
    void rejectsPathAndParameterVariations(String path) throws Exception {
        for (String perfil : new String[] {"ALUNO", "RESPONSAVEL"}) {
            assertThat(status(path, TestTokens.bearer(perfil, PROPRIO))).as(perfil + " " + path).isEqualTo(403);
        }
    }

    /** No referenciaId (or one that is not a number): notifications only. */
    @ParameterizedTest
    @ValueSource(strings = {"ALUNO", "RESPONSAVEL"})
    void tokenWithoutReferenciaIdOnlyReadsNotifications(String perfil) throws Exception {
        for (String token : new String[] {TestTokens.bearer(perfil), TestTokens.bearer(perfil, "12")}) {
            assertForbidden("/api/alunos/12", token);
            assertForbidden("/api/alunos/12/boletim?anoLetivo=2026", token);
            assertForbidden("/api/frequencias/alunos/12?anoLetivo=2026", token);
            assertForbidden("/api/matriculas?alunoId=12", token);
            assertForbidden("/api/turmas", token);

            assertThat(status("/api/notificacoes", token)).isEqualTo(200);
            assertThat(status("/api/notificacoes/nao-lidas/contagem", token)).isEqualTo(200);
            assertThat(send("PATCH", "/api/notificacoes/7/lida", token).statusCode()).isEqualTo(200);
            assertThat(send("POST", "/api/notificacoes/lidas", token).statusCode()).isEqualTo(200);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALUNO", "RESPONSAVEL"})
    void cannotChangeTheirOwnData(String perfil) throws Exception {
        String token = TestTokens.bearer(perfil, PROPRIO);

        assertThat(send("PUT", "/api/alunos/12", token).statusCode()).isEqualTo(403);
        assertThat(send("DELETE", "/api/alunos/12", token).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/matriculas", token).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/frequencias/chamadas", token).statusCode()).isEqualTo(403);
    }

    private void assertForbidden(String path, String token) throws Exception {
        HttpResponse<String> response = send("GET", path, token);
        assertThat(response.statusCode()).as(path).isEqualTo(403);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE)).as(path).hasValue("application/problem+json");
        assertThat(response.body()).as(path)
                .contains("\"status\":403")
                .contains("\"title\":\"" + AcessoNegadoHandler.TITULO + "\"")
                .contains("\"instance\":\"" + path.replaceFirst("\\?.*", "") + "\"");
    }

    private int status(String path, String token) throws Exception {
        return send("GET", path, token).statusCode();
    }

    private HttpResponse<String> send(String method, String path, String authorization) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.AUTHORIZATION, authorization);
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString("{}"))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json");
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
