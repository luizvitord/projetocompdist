package com.distrischool.gestao_administrativa.disciplina;

import com.distrischool.gestao_administrativa.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_administrativa.common.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

@WebMvcTest(DisciplinaController.class)
class DisciplinaControllerTest {

    private static final String CORPO_VALIDO = """
            {"codigo": "MAT", "nome": "Matemática", "cargaHoraria": 80}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DisciplinaService disciplinaService;

    private static DisciplinaResponse resposta() {
        return new DisciplinaResponse(1L, "MAT", "Matemática", 80, true, Instant.now(), Instant.now());
    }

    @Test
    void criaDisciplinaERetorna201ComLocation() throws Exception {
        when(disciplinaService.criar(any())).thenReturn(resposta());

        mockMvc.perform(post("/api/disciplinas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/disciplinas/1"))
                .andExpect(jsonPath("$.codigo").value("MAT"))
                .andExpect(jsonPath("$.ativa").value(true));
    }

    @Test
    void rejeitaDisciplinaComCamposInvalidos() throws Exception {
        mockMvc.perform(post("/api/disciplinas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo": "CODIGO-MUITO-LONGO-PARA-O-CAMPO", "cargaHoraria": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigo").exists())
                .andExpect(jsonPath("$.erros.nome").exists())
                .andExpect(jsonPath("$.erros.cargaHoraria").exists());

        verify(disciplinaService, never()).criar(any());
    }

    @Test
    void aceitaDisciplinaSemCargaHoraria() throws Exception {
        when(disciplinaService.criar(any())).thenReturn(resposta());

        mockMvc.perform(post("/api/disciplinas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo": "MAT", "nome": "Matemática"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void retorna409QuandoCodigoJaExiste() throws Exception {
        when(disciplinaService.criar(any()))
                .thenThrow(new RegraNegocioException("Já existe uma disciplina com o código MAT"));

        mockMvc.perform(post("/api/disciplinas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void listaSomenteAtivasPorPadrao() throws Exception {
        when(disciplinaService.buscar(eq("mat"), eq(true), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/disciplinas").param("nome", "mat"))
                .andExpect(status().isOk());

        verify(disciplinaService).buscar(eq("mat"), eq(true), any());
    }

    @Test
    void listaDisciplinasInativasQuandoSolicitado() throws Exception {
        when(disciplinaService.buscar(isNull(), eq(false), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/disciplinas").param("ativa", "false"))
                .andExpect(status().isOk());

        verify(disciplinaService).buscar(isNull(), eq(false), any());
    }

    @Test
    void buscaPorId() throws Exception {
        when(disciplinaService.buscarPorId(1L)).thenReturn(resposta());

        mockMvc.perform(get("/api/disciplinas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Matemática"));
    }

    @Test
    void retorna404QuandoDisciplinaNaoExiste() throws Exception {
        when(disciplinaService.buscarPorId(99L))
                .thenThrow(new RecursoNaoEncontradoException("Disciplina", 99L));

        mockMvc.perform(get("/api/disciplinas/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizaDisciplina() throws Exception {
        when(disciplinaService.atualizar(eq(1L), any())).thenReturn(resposta());

        mockMvc.perform(put("/api/disciplinas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void excluiLogicamenteERetorna204() throws Exception {
        mockMvc.perform(delete("/api/disciplinas/1"))
                .andExpect(status().isNoContent());

        verify(disciplinaService).excluir(1L);
    }

    @Test
    void excluirRetorna409QuandoDisciplinaEstaOfertadaEmTurma() throws Exception {
        doThrow(new RegraNegocioException("A disciplina MAT está ofertada em uma ou mais turmas"))
                .when(disciplinaService).excluir(1L);

        mockMvc.perform(delete("/api/disciplinas/1"))
                .andExpect(status().isConflict());
    }

    @Test
    void excluirRetorna404QuandoDisciplinaNaoExiste() throws Exception {
        doThrow(new RecursoNaoEncontradoException("Disciplina", 99L))
                .when(disciplinaService).excluir(99L);

        mockMvc.perform(delete("/api/disciplinas/99"))
                .andExpect(status().isNotFound());
    }
}
