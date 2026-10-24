package com.distrischool.gestao_administrativa.turma;

import com.distrischool.gestao_administrativa.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_administrativa.common.RegraNegocioException;
import com.distrischool.gestao_administrativa.turma.TurmaResponse.DisciplinaOfertada;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TurmaController.class)
class TurmaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TurmaService turmaService;

    private static TurmaResponse resposta() {
        return new TurmaResponse(
                12L, "9A-2026", "9º Ano A", 2026, Turno.MANHA, 35,
                List.of(new TurmaResponse.ProfessorResumo(7L, "Carlos Lima")),
                List.of(new DisciplinaOfertada(3L, "MAT", "Matemática", 7L, "Carlos Lima")),
                Instant.now(), Instant.now());
    }

    @Test
    void respostaDaTurmaTrazDisciplinasESemQuantidadeAlunos() throws Exception {
        when(turmaService.buscarPorId(12L)).thenReturn(resposta());

        mockMvc.perform(get("/api/turmas/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeAlunos").doesNotExist())
                .andExpect(jsonPath("$.disciplinas[0].disciplinaId").value(3))
                .andExpect(jsonPath("$.disciplinas[0].codigo").value("MAT"))
                .andExpect(jsonPath("$.disciplinas[0].nome").value("Matemática"))
                .andExpect(jsonPath("$.disciplinas[0].professorId").value(7))
                .andExpect(jsonPath("$.disciplinas[0].professorNome").value("Carlos Lima"));
    }

    @Test
    void ofertaDisciplinaComProfessor() throws Exception {
        when(turmaService.ofertarDisciplina(12L, 3L, 7L)).thenReturn(resposta());

        mockMvc.perform(put("/api/turmas/12/disciplinas/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"professorId\": 7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disciplinas[0].professorId").value(7));

        verify(turmaService).ofertarDisciplina(12L, 3L, 7L);
    }

    @Test
    void ofertaDisciplinaSemCorpo() throws Exception {
        when(turmaService.ofertarDisciplina(12L, 3L, null)).thenReturn(resposta());

        mockMvc.perform(put("/api/turmas/12/disciplinas/3"))
                .andExpect(status().isOk());

        verify(turmaService).ofertarDisciplina(12L, 3L, null);
    }

    @Test
    void ofertaDisciplinaComCorpoSemProfessor() throws Exception {
        when(turmaService.ofertarDisciplina(12L, 3L, null)).thenReturn(resposta());

        mockMvc.perform(put("/api/turmas/12/disciplinas/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(turmaService).ofertarDisciplina(eq(12L), eq(3L), isNull());
    }

    @Test
    void ofertaRetorna409QuandoDisciplinaInativa() throws Exception {
        when(turmaService.ofertarDisciplina(12L, 3L, null))
                .thenThrow(new RegraNegocioException("A disciplina MAT está inativa e não pode ser ofertada"));

        mockMvc.perform(put("/api/turmas/12/disciplinas/3"))
                .andExpect(status().isConflict());
    }

    @Test
    void ofertaRetorna404QuandoProfessorNaoExiste() throws Exception {
        when(turmaService.ofertarDisciplina(12L, 3L, 99L))
                .thenThrow(new RecursoNaoEncontradoException("Professor", 99L));

        mockMvc.perform(put("/api/turmas/12/disciplinas/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"professorId\": 99}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retiraOfertaERetorna204() throws Exception {
        mockMvc.perform(delete("/api/turmas/12/disciplinas/3"))
                .andExpect(status().isNoContent());

        verify(turmaService).retirarDisciplina(12L, 3L);
    }

    @Test
    void retiraOfertaRetorna404QuandoDisciplinaNaoEstaNaTurma() throws Exception {
        doThrow(new RecursoNaoEncontradoException("Disciplina na turma 9A-2026", 3L))
                .when(turmaService).retirarDisciplina(12L, 3L);

        mockMvc.perform(delete("/api/turmas/12/disciplinas/3"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rotasDeAlunosNaoExistemMais() throws Exception {
        mockMvc.perform(put("/api/turmas/12/alunos/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/turmas/12/alunos/1"))
                .andExpect(status().isNotFound());
    }
}
