package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.frequencia.ResumoFrequencia;
import com.distrischool.gestao_escolar.matricula.StatusMatricula;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BoletimController.class)
class BoletimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BoletimService boletimService;

    @Test
    void boletimPorDisciplina() throws Exception {
        ResumoFrequencia frequencia = new ResumoFrequencia(10, 9, 1, 0, new BigDecimal("90.00"), false);
        List<BoletimResponse.Bimestre> bimestres = List.of(
                new BoletimResponse.Bimestre(1, new BigDecimal("7.50"), new BigDecimal("4.67"), new BigDecimal("7.50"), 0),
                new BoletimResponse.Bimestre(2, null, null, null, 1),
                new BoletimResponse.Bimestre(3, null, null, null, 0),
                new BoletimResponse.Bimestre(4, null, null, null, 0));
        when(boletimService.boletim(1L, 2026)).thenReturn(new BoletimResponse(1L, 2026, new BigDecimal("6.0"),
                new BigDecimal("75"), SituacaoBoletim.CURSANDO, frequencia,
                List.of(new BoletimResponse.MatriculaResumo(4L, 12L, "9A-2026", StatusMatricula.ATIVA,
                        LocalDate.of(2026, 2, 1), null)),
                List.of(new BoletimResponse.Disciplina(3L, "MAT", "Matemática", bimestres, new BigDecimal("7.50"),
                        frequencia, 1, SituacaoBoletim.CURSANDO))));

        mockMvc.perform(get("/api/alunos/1/boletim").param("anoLetivo", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CURSANDO"))
                .andExpect(jsonPath("$.disciplinas[0].bimestres.length()").value(4))
                .andExpect(jsonPath("$.disciplinas[0].bimestres[0].media").value(7.50))
                .andExpect(jsonPath("$.disciplinas[0].mediaAnual").value(7.50))
                .andExpect(jsonPath("$.disciplinas[0].frequencia.percentual").value(90.00))
                .andExpect(jsonPath("$.disciplinas[0].avaliacoesPendentes").value(1))
                .andExpect(jsonPath("$.matriculas[0].status").value("ATIVA"));
    }

    @Test
    void exigeAnoLetivo() throws Exception {
        mockMvc.perform(get("/api/alunos/1/boletim"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alunoSemMatriculaNoAnoRetorna404() throws Exception {
        when(boletimService.boletim(1L, 2025))
                .thenThrow(new RecursoNaoEncontradoException("Matrícula do aluno 1 no ano letivo", 2025));

        mockMvc.perform(get("/api/alunos/1/boletim").param("anoLetivo", "2025"))
                .andExpect(status().isNotFound());
    }
}
