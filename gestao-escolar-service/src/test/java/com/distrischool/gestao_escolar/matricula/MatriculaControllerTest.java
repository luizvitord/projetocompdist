package com.distrischool.gestao_escolar.matricula;

import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.common.RegraNegocioException;
import com.distrischool.gestao_escolar.common.ServicoIndisponivelException;
import com.distrischool.gestao_escolar.common.ValidacaoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatriculaController.class)
class MatriculaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatriculaService matriculaService;

    private static MatriculaResponse resposta(Long id, Long turmaId, StatusMatricula status, Long origemId) {
        return new MatriculaResponse(id, 1L, turmaId, 2026, status, LocalDate.of(2026, 2, 1), null, null,
                origemId, Instant.now(), Instant.now());
    }

    @Test
    void criaMatriculaERetorna201ComLocation() throws Exception {
        when(matriculaService.criar(new MatriculaRequest(1L, 12L)))
                .thenReturn(resposta(4L, 12L, StatusMatricula.ATIVA, null));

        mockMvc.perform(post("/api/matriculas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alunoId\": 1, \"turmaId\": 12}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/matriculas/4"))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.anoLetivo").value(2026));
    }

    @Test
    void rejeitaMatriculaSemAlunoETurma() throws Exception {
        mockMvc.perform(post("/api/matriculas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.alunoId").exists())
                .andExpect(jsonPath("$.erros.turmaId").exists());

        verify(matriculaService, never()).criar(any());
    }

    @Test
    void turmaSemVagaRetorna409() throws Exception {
        when(matriculaService.criar(any())).thenThrow(new RegraNegocioException("A turma 9A-2026 não tem vagas (capacidade 35)"));

        mockMvc.perform(post("/api/matriculas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alunoId\": 1, \"turmaId\": 12}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A turma 9A-2026 não tem vagas (capacidade 35)"));
    }

    @Test
    void outroServicoIndisponivelRetorna503ComProblemDetail() throws Exception {
        when(matriculaService.criar(any()))
                .thenThrow(new ServicoIndisponivelException("gestao-administrativa-service indisponível; tente novamente em instantes", null));

        mockMvc.perform(post("/api/matriculas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alunoId\": 1, \"turmaId\": 20}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("gestao-administrativa-service indisponível; tente novamente em instantes"));
    }

    @Test
    void listaPaginadaComFiltros() throws Exception {
        when(matriculaService.buscar(eq(1L), eq(12L), eq(2026), eq(StatusMatricula.ATIVA), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resposta(4L, 12L, StatusMatricula.ATIVA, null)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/matriculas")
                        .param("alunoId", "1").param("turmaId", "12")
                        .param("anoLetivo", "2026").param("status", "ATIVA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(4))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void buscaPorIdRetorna404QuandoNaoExiste() throws Exception {
        when(matriculaService.buscarPorId(99L)).thenThrow(new RecursoNaoEncontradoException("Matrícula", 99L));

        mockMvc.perform(get("/api/matriculas/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void alteraStatus() throws Exception {
        when(matriculaService.alterarStatus(4L, new AlteracaoStatusRequest(StatusMatricula.TRANCADA, "Viagem")))
                .thenReturn(resposta(4L, 12L, StatusMatricula.TRANCADA, null));

        mockMvc.perform(patch("/api/matriculas/4/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"TRANCADA\", \"motivo\": \"Viagem\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRANCADA"));
    }

    @Test
    void motivoAusenteRetorna400ComOCampo() throws Exception {
        when(matriculaService.alterarStatus(eq(4L), any()))
                .thenThrow(ValidacaoException.campo("motivo", "é obrigatório para o status TRANCADA"));

        mockMvc.perform(patch("/api/matriculas/4/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"TRANCADA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.motivo").value("é obrigatório para o status TRANCADA"));
    }

    @Test
    void statusDesconhecidoRetorna400() throws Exception {
        mockMvc.perform(patch("/api/matriculas/4/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"EXPULSA\"}"))
                .andExpect(status().isBadRequest());

        verify(matriculaService, never()).alterarStatus(any(), any());
    }

    @Test
    void transicaoInvalidaRetorna409() throws Exception {
        when(matriculaService.alterarStatus(eq(4L), any()))
                .thenThrow(new RegraNegocioException("Transição de status inválida: CANCELADA → ATIVA"));

        mockMvc.perform(patch("/api/matriculas/4/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ATIVA\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void transferenciaRetorna201ComANovaMatricula() throws Exception {
        when(matriculaService.transferir(4L, new TransferenciaRequest(15L, "Mudança de turno")))
                .thenReturn(resposta(5L, 15L, StatusMatricula.ATIVA, 4L));

        mockMvc.perform(post("/api/matriculas/4/transferencia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"turmaDestinoId\": 15, \"motivo\": \"Mudança de turno\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/matriculas/5"))
                .andExpect(jsonPath("$.turmaId").value(15))
                .andExpect(jsonPath("$.matriculaOrigemId").value(4));
    }

    @Test
    void transferenciaSemMotivoRetorna400() throws Exception {
        mockMvc.perform(post("/api/matriculas/4/transferencia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"turmaDestinoId\": 15}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.motivo").exists());
    }

    @Test
    void naoExisteExclusaoDeMatricula() throws Exception {
        mockMvc.perform(delete("/api/matriculas/4"))
                .andExpect(status().isMethodNotAllowed());
    }
}
