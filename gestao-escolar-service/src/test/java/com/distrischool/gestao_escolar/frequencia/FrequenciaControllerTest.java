package com.distrischool.gestao_escolar.frequencia;

import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.common.RegraNegocioException;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FrequenciaController.class)
class FrequenciaControllerTest {

    private static final String CHAMADA = """
            {"turmaId": 12, "disciplinaId": 3, "data": "2026-10-20", "professorId": 7,
             "registros": [{"matriculaId": 41, "status": "PRESENTE"},
                           {"matriculaId": 42, "status": "FALTA", "observacao": "atestado"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChamadaService chamadaService;

    @MockitoBean
    private RelatorioFrequenciaService relatorioService;

    private static ChamadaResponse resposta() {
        return new ChamadaResponse(500L, 12L, 3L, LocalDate.of(2026, 10, 20), 7L,
                List.of(new ChamadaResponse.Registro(41L, StatusPresenca.PRESENTE, null),
                        new ChamadaResponse.Registro(42L, StatusPresenca.FALTA, "atestado")),
                Instant.now(), Instant.now());
    }

    @Test
    void registraChamadaERetorna201ComLocation() throws Exception {
        when(chamadaService.registrar(any())).thenReturn(resposta());

        mockMvc.perform(post("/api/frequencias/chamadas").contentType(MediaType.APPLICATION_JSON).content(CHAMADA))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/frequencias/chamadas/500"))
                .andExpect(jsonPath("$.registros[1].status").value("FALTA"))
                .andExpect(jsonPath("$.registros[1].observacao").value("atestado"));
    }

    @Test
    void rejeitaChamadaSemRegistrosComDataFuturaEStatusAusente() throws Exception {
        mockMvc.perform(post("/api/frequencias/chamadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turmaId": 12, "disciplinaId": 3, "data": "2999-01-01", "registros": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.data").exists())
                .andExpect(jsonPath("$.erros.registros").exists());

        mockMvc.perform(post("/api/frequencias/chamadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turmaId": 12, "disciplinaId": 3, "data": "2026-10-20",
                                 "registros": [{"matriculaId": 41}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros['registros[0].status']").exists());

        verify(chamadaService, never()).registrar(any());
    }

    @Test
    void divergenciaDeMatriculasRetorna400ComAsListas() throws Exception {
        Map<String, Object> divergencias = new LinkedHashMap<>();
        divergencias.put("matriculasFaltando", List.of(43L));
        divergencias.put("matriculasNaoEsperadas", List.of(99L));
        divergencias.put("matriculasDuplicadas", List.of());
        when(chamadaService.registrar(any())).thenThrow(new ValidacaoException(
                "A chamada deve conter exatamente as matrículas ATIVA da turma, cada uma uma vez", divergencias));

        mockMvc.perform(post("/api/frequencias/chamadas").contentType(MediaType.APPLICATION_JSON).content(CHAMADA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.matriculasFaltando[0]").value(43))
                .andExpect(jsonPath("$.matriculasNaoEsperadas[0]").value(99))
                .andExpect(jsonPath("$.matriculasDuplicadas").isEmpty());
    }

    @Test
    void chamadaRepetidaRetorna409() throws Exception {
        when(chamadaService.registrar(any())).thenThrow(new RegraNegocioException("Já existe uma chamada"));

        mockMvc.perform(post("/api/frequencias/chamadas").contentType(MediaType.APPLICATION_JSON).content(CHAMADA))
                .andExpect(status().isConflict());
    }

    @Test
    void listaChamadasComFiltrosDeData() throws Exception {
        when(chamadaService.buscar(eq(12L), eq(3L), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resposta()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/frequencias/chamadas")
                        .param("turmaId", "12").param("disciplinaId", "3")
                        .param("dataInicio", "2026-10-01").param("dataFim", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(500))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void buscaChamadaInexistenteRetorna404() throws Exception {
        when(chamadaService.buscarPorId(9L)).thenThrow(new RecursoNaoEncontradoException("Chamada", 9L));

        mockMvc.perform(get("/api/frequencias/chamadas/9"))
                .andExpect(status().isNotFound());
    }

    @Test
    void editaChamada() throws Exception {
        when(chamadaService.atualizar(eq(500L), any())).thenReturn(resposta());

        mockMvc.perform(put("/api/frequencias/chamadas/500").contentType(MediaType.APPLICATION_JSON).content(CHAMADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(500));
    }

    @Test
    void relatorioPorAluno() throws Exception {
        when(relatorioService.relatorio(eq(12L), eq(3L), isNull(), isNull())).thenReturn(new RelatorioFrequenciaResponse(
                12L, 3L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), new BigDecimal("75"),
                List.of(new RelatorioFrequenciaResponse.Aluno(1L, "2026000001", "Maria Silva", 4, 2, 2, 0,
                        new BigDecimal("50.00"), true))));

        mockMvc.perform(get("/api/frequencias/relatorio").param("turmaId", "12").param("disciplinaId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alunos[0].totalAulas").value(4))
                .andExpect(jsonPath("$.alunos[0].percentual").value(50.00))
                .andExpect(jsonPath("$.alunos[0].abaixoDoMinimo").value(true));
    }

    @Test
    void relatorioExigeTurma() throws Exception {
        mockMvc.perform(get("/api/frequencias/relatorio"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void frequenciaDoAlunoPorDisciplinaEGeral() throws Exception {
        ResumoFrequencia mat = new ResumoFrequencia(10, 9, 1, 0, new BigDecimal("90.00"), false);
        when(relatorioService.porAluno(1L, 2026)).thenReturn(new FrequenciaAlunoResponse(1L, 2026, new BigDecimal("75"),
                List.of(new FrequenciaAlunoResponse.Disciplina(3L, "MAT", "Matemática", mat)), mat));

        mockMvc.perform(get("/api/frequencias/alunos/1").param("anoLetivo", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disciplinas[0].frequencia.percentual").value(90.00))
                .andExpect(jsonPath("$.geral.totalAulas").value(10));
    }

    @Test
    void frequenciaDoAlunoExigeAnoLetivo() throws Exception {
        mockMvc.perform(get("/api/frequencias/alunos/1"))
                .andExpect(status().isBadRequest());
    }
}
