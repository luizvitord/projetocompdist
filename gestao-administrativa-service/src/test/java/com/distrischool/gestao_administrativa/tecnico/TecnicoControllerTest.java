package com.distrischool.gestao_administrativa.tecnico;

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
import java.time.LocalDate;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TecnicoController.class)
class TecnicoControllerTest {

    private static final String CORPO_VALIDO = """
            {"nome": "Ana Souza", "cpf": "529.982.247-25", "cargo": "SECRETARIO",
             "setor": "Secretaria", "email": "ana@example.com", "telefone": "11988887777",
             "dataAdmissao": "2024-02-01"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TecnicoService tecnicoService;

    private static TecnicoResponse resposta(boolean ativo) {
        return new TecnicoResponse(
                1L, "Ana Souza", "52998224725", Cargo.SECRETARIO, "Secretaria", "ana@example.com",
                "11988887777", LocalDate.of(2024, 2, 1), ativo, Instant.now(), Instant.now());
    }

    @Test
    void criaTecnicoERetorna201ComLocation() throws Exception {
        when(tecnicoService.criar(any())).thenReturn(resposta(true));

        mockMvc.perform(post("/api/tecnicos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/tecnicos/1"))
                .andExpect(jsonPath("$.cargo").value("SECRETARIO"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void rejeitaTecnicoComCamposInvalidos() throws Exception {
        String amanha = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(post("/api/tecnicos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cpf": "123.456.789-00", "cargo": "SECRETARIO",
                                 "email": "nao-e-email", "dataAdmissao": "%s"}
                                """.formatted(amanha)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").exists())
                .andExpect(jsonPath("$.erros.cpf").exists())
                .andExpect(jsonPath("$.erros.setor").exists())
                .andExpect(jsonPath("$.erros.email").exists())
                .andExpect(jsonPath("$.erros.dataAdmissao").exists());

        verify(tecnicoService, never()).criar(any());
    }

    @Test
    void retorna409QuandoCpfJaExisteSemExporOCpf() throws Exception {
        when(tecnicoService.criar(any()))
                .thenThrow(new RegraNegocioException("Já existe um técnico administrativo com este CPF"));

        mockMvc.perform(post("/api/tecnicos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("52998224725"))));
    }

    @Test
    void listaSomenteAtivosPorPadrao() throws Exception {
        when(tecnicoService.buscar(isNull(), eq(Cargo.TECNICO_TI), isNull(), eq(true), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/tecnicos").param("cargo", "TECNICO_TI"))
                .andExpect(status().isOk());

        verify(tecnicoService).buscar(isNull(), eq(Cargo.TECNICO_TI), isNull(), eq(true), any());
    }

    @Test
    void retorna404QuandoTecnicoNaoExiste() throws Exception {
        when(tecnicoService.buscarPorId(99L))
                .thenThrow(new RecursoNaoEncontradoException("Técnico administrativo", 99L));

        mockMvc.perform(get("/api/tecnicos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizaTecnico() throws Exception {
        when(tecnicoService.atualizar(eq(1L), any())).thenReturn(resposta(true));

        mockMvc.perform(put("/api/tecnicos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void excluiLogicamenteERetorna204() throws Exception {
        mockMvc.perform(delete("/api/tecnicos/1"))
                .andExpect(status().isNoContent());

        verify(tecnicoService).excluir(1L);
    }

    @Test
    void excluirRetorna404QuandoTecnicoNaoExiste() throws Exception {
        doThrow(new RecursoNaoEncontradoException("Técnico administrativo", 99L))
                .when(tecnicoService).excluir(99L);

        mockMvc.perform(delete("/api/tecnicos/99"))
                .andExpect(status().isNotFound());
    }
}
