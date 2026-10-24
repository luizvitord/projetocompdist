package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.common.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AvaliacaoController.class)
class AvaliacaoControllerTest {

    private static final String AVALIACAO = """
            {"turmaId": 12, "disciplinaId": 3, "bimestre": 1, "tipo": "PROVA",
             "descricao": "Prova 1", "peso": 2, "data": "2026-04-10"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvaliacaoService avaliacaoService;

    @MockitoBean
    private NotaService notaService;

    private static AvaliacaoResponse resposta() {
        return new AvaliacaoResponse(9L, 12L, 3L, 1, TipoAvaliacao.PROVA, "Prova 1", new BigDecimal("2.00"),
                new BigDecimal("10.00"), LocalDate.of(2026, 4, 10), Instant.now(), Instant.now());
    }

    private static NotaResponse nota(Long id, Long matriculaId, String valor) {
        return new NotaResponse(id, 9L, matriculaId, new BigDecimal(valor), Instant.now(), Instant.now());
    }

    @Test
    void criaAvaliacaoERetorna201ComLocation() throws Exception {
        when(avaliacaoService.criar(any())).thenReturn(resposta());

        mockMvc.perform(post("/api/avaliacoes").contentType(MediaType.APPLICATION_JSON).content(AVALIACAO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/avaliacoes/9"))
                .andExpect(jsonPath("$.peso").value(2.00))
                .andExpect(jsonPath("$.notaMaxima").value(10.00));
    }

    @Test
    void rejeitaBimestreForaDaFaixaPesoZeroETipoAusente() throws Exception {
        mockMvc.perform(post("/api/avaliacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turmaId": 12, "disciplinaId": 3, "bimestre": 5, "peso": 0, "notaMaxima": -1,
                                 "data": "2026-04-10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.bimestre").exists())
                .andExpect(jsonPath("$.erros.tipo").exists())
                .andExpect(jsonPath("$.erros.peso").exists())
                .andExpect(jsonPath("$.erros.notaMaxima").exists());

        verify(avaliacaoService, never()).criar(any());
    }

    @Test
    void segundaRecuperacaoRetorna409() throws Exception {
        when(avaliacaoService.criar(any())).thenThrow(new RegraNegocioException("Já existe uma recuperação"));

        mockMvc.perform(post("/api/avaliacoes").contentType(MediaType.APPLICATION_JSON).content(AVALIACAO))
                .andExpect(status().isConflict());
    }

    @Test
    void listaComFiltros() throws Exception {
        when(avaliacaoService.buscar(eq(12L), eq(3L), eq(1), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resposta()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/avaliacoes").param("turmaId", "12").param("disciplinaId", "3").param("bimestre", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(9))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void atualizaEBuscaPorId() throws Exception {
        when(avaliacaoService.atualizar(eq(9L), any())).thenReturn(resposta());
        when(avaliacaoService.buscarPorId(9L)).thenReturn(resposta());

        mockMvc.perform(put("/api/avaliacoes/9").contentType(MediaType.APPLICATION_JSON).content(AVALIACAO))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/avaliacoes/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PROVA"));
    }

    @Test
    void excluirSemNotasRetorna204EComNotasRetorna409() throws Exception {
        mockMvc.perform(delete("/api/avaliacoes/9"))
                .andExpect(status().isNoContent());

        doThrow(new RegraNegocioException("A avaliação já tem notas e não pode ser excluída"))
                .when(avaliacaoService).excluir(10L);
        mockMvc.perform(delete("/api/avaliacoes/10"))
                .andExpect(status().isConflict());
    }

    @Test
    void lancaNotasEmLoteERetorna201() throws Exception {
        when(notaService.lancar(eq(9L), anyList())).thenReturn(List.of(nota(100L, 4L, "8.50")));

        mockMvc.perform(post("/api/avaliacoes/9/notas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"matriculaId\": 4, \"valor\": 8.5}]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].valor").value(8.50));

        verify(notaService).lancar(9L, List.of(new LancamentoNotaRequest(4L, new BigDecimal("8.5"))));
    }

    @Test
    void lancamentoValidaCadaItemNoMesmoFormatoDeErros() throws Exception {
        mockMvc.perform(post("/api/avaliacoes/9/notas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"matriculaId\": 4, \"valor\": 8.5}, {\"matriculaId\": 5, \"valor\": -1}, {\"valor\": 7}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros['notas[1].valor']").exists())
                .andExpect(jsonPath("$.erros['notas[2].matriculaId']").exists());

        mockMvc.perform(post("/api/avaliacoes/9/notas").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.notas").exists());

        verify(notaService, never()).lancar(any(), anyList());
    }

    @Test
    void lancamentoComMatriculaQueJaTemNotaRetorna409() throws Exception {
        when(notaService.lancar(eq(9L), anyList()))
                .thenThrow(new RegraNegocioException("As matrículas [4] já têm nota nesta avaliação"));

        mockMvc.perform(post("/api/avaliacoes/9/notas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"matriculaId\": 4, \"valor\": 8.5}]"))
                .andExpect(status().isConflict());
    }

    @Test
    void listaNotasDaAvaliacao() throws Exception {
        when(notaService.listar(9L)).thenReturn(List.of(nota(100L, 4L, "8.50"), nota(101L, 5L, "6.00")));

        mockMvc.perform(get("/api/avaliacoes/9/notas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].matriculaId").value(5));
    }
}
