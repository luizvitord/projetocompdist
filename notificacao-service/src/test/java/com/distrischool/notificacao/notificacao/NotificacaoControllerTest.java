package com.distrischool.notificacao.notificacao;

import com.distrischool.notificacao.common.RecursoNaoEncontradoException;
import com.distrischool.notificacao.seguranca.SecurityConfig;
import com.distrischool.notificacao.seguranca.TestTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller com a segurança real: os tokens são assinados como o usuarios-service faz. */
@WebMvcTest(NotificacaoController.class)
@Import(SecurityConfig.class)
class NotificacaoControllerTest {

    private static final Destinatario RESPONSAVEL_12 = Destinatario.de(Perfil.RESPONSAVEL, 12L);
    private static final String TOKEN_RESPONSAVEL_12 = TestTokens.bearer("RESPONSAVEL", 12L);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificacaoService notificacaoService;

    @Test
    void semTokenRetorna401EmTodasAsRotas() throws Exception {
        mockMvc.perform(get("/api/notificacoes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notificacoes/nao-lidas/contagem")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notificacoes/1/lida")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/notificacoes/lidas")).andExpect(status().isUnauthorized());

        verifyNoInteractions(notificacaoService);
    }

    @Test
    void tokenInvalidoRetorna401() throws Exception {
        String outroSegredo = TestTokens.token("ADMIN", null, "outro-segredo-qualquer-com-mais-de-32-bytes",
                Instant.now().plus(Duration.ofHours(1)));
        String expirado = TestTokens.token("ADMIN", null, TestTokens.SEGREDO, Instant.now().minus(Duration.ofHours(1)));
        String semPerfil = TestTokens.token(null, 12L);
        String perfilDesconhecido = TestTokens.token("VISITANTE", 12L);

        for (String token : List.of(outroSegredo, expirado, semPerfil, perfilDesconhecido)) {
            mockMvc.perform(get("/api/notificacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(notificacaoService);
    }

    @Test
    void listaAsNotificacoesDoDestinatarioDoTokenMaisRecentesPrimeiro() throws Exception {
        when(notificacaoService.buscar(eq(RESPONSAVEL_12), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resposta(7L, null)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/notificacoes").header(HttpHeaders.AUTHORIZATION, TOKEN_RESPONSAVEL_12))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].tipo").value("NOTA_LANCADA"))
                .andExpect(jsonPath("$.content[0].titulo").value("Nova nota"))
                .andExpect(jsonPath("$.content[0].mensagem").value("Nova nota em Matemática (1º bimestre): 8,5"))
                .andExpect(jsonPath("$.content[0].link").value("/alunos/12"))
                .andExpect(jsonPath("$.content[0].lida").value(false))
                .andExpect(jsonPath("$.content[0].eventoHash").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(notificacaoService).buscar(eq(RESPONSAVEL_12), eq(false), pagina.capture());
        assertThat(pagina.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("criadaEm"), Sort.Order.desc("id")));
    }

    @Test
    void filtraSomenteNaoLidasERespeitaAPaginacao() throws Exception {
        when(notificacaoService.buscar(any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/notificacoes").param("somenteNaoLidas", "true").param("page", "2").param("size", "5")
                        // A ordenação é fixa: o parâmetro sort é ignorado
                        .param("sort", "titulo,asc")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN_RESPONSAVEL_12))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(notificacaoService).buscar(eq(RESPONSAVEL_12), eq(true), pagina.capture());
        assertThat(pagina.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pagina.getValue().getPageSize()).isEqualTo(5);
        assertThat(pagina.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("criadaEm"), Sort.Order.desc("id")));
    }

    @Test
    void destinatarioVemDoTokenENaoDeParametros() throws Exception {
        when(notificacaoService.contarNaoLidas(any())).thenReturn(0L);

        mockMvc.perform(get("/api/notificacoes/nao-lidas/contagem")
                        .param("perfil", "ADMIN").param("referenciaId", "99").param("destinatario", "ALUNO:99")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN_RESPONSAVEL_12))
                .andExpect(status().isOk());

        verify(notificacaoService).contarNaoLidas(RESPONSAVEL_12);
    }

    @Test
    void adminRepresentaACoordenacaoMesmoComReferenciaNoToken() throws Exception {
        when(notificacaoService.contarNaoLidas(Destinatario.admin())).thenReturn(3L);

        mockMvc.perform(get("/api/notificacoes/nao-lidas/contagem")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("ADMIN", 3L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void contaAsNaoLidas() throws Exception {
        when(notificacaoService.contarNaoLidas(RESPONSAVEL_12)).thenReturn(3L);

        mockMvc.perform(get("/api/notificacoes/nao-lidas/contagem")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN_RESPONSAVEL_12))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void marcaComoLida() throws Exception {
        when(notificacaoService.marcarComoLida(RESPONSAVEL_12, 7L))
                .thenReturn(resposta(7L, Instant.parse("2026-11-10T12:00:00Z")));

        mockMvc.perform(patch("/api/notificacoes/7/lida").header(HttpHeaders.AUTHORIZATION, TOKEN_RESPONSAVEL_12))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.lida").value(true))
                .andExpect(jsonPath("$.lidaEm").value("2026-11-10T12:00:00Z"));
    }

    @Test
    void notificacaoDeOutroDestinatarioRetorna404() throws Exception {
        // O service procura pelo id E pelo destinatário do token: a notificação 7 é de outra pessoa
        when(notificacaoService.marcarComoLida(Destinatario.de(Perfil.ALUNO, 99L), 7L))
                .thenThrow(new RecursoNaoEncontradoException("Notificação", 7L));

        mockMvc.perform(patch("/api/notificacoes/7/lida")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("ALUNO", 99L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Notificação não encontrado(a): 7"));
    }

    @Test
    void marcaTodasComoLidas() throws Exception {
        when(notificacaoService.marcarTodasComoLidas(RESPONSAVEL_12)).thenReturn(2);

        mockMvc.perform(post("/api/notificacoes/lidas").header(HttpHeaders.AUTHORIZATION, TOKEN_RESPONSAVEL_12))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atualizadas").value(2));
    }

    @Test
    void rotasForaDaApiDeNotificacoesSaoNegadas() throws Exception {
        mockMvc.perform(get("/api/alunos").header(HttpHeaders.AUTHORIZATION, TOKEN_RESPONSAVEL_12))
                .andExpect(status().isForbidden());
    }

    private static NotificacaoResponse resposta(Long id, Instant lidaEm) {
        return new NotificacaoResponse(id, TipoNotificacao.NOTA_LANCADA, "Nova nota",
                "Nova nota em Matemática (1º bimestre): 8,5", "/alunos/12",
                Instant.parse("2026-11-10T11:00:00Z"), lidaEm, lidaEm != null);
    }
}
