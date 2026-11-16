package com.distrischool.gestao_administrativa.common;

import com.distrischool.gestao_administrativa.disciplina.DisciplinaController;
import com.distrischool.gestao_administrativa.disciplina.DisciplinaService;
import com.distrischool.gestao_administrativa.professor.ProfessorController;
import com.distrischool.gestao_administrativa.professor.ProfessorService;
import com.distrischool.gestao_administrativa.tecnico.Cargo;
import com.distrischool.gestao_administrativa.tecnico.TecnicoController;
import com.distrischool.gestao_administrativa.tecnico.TecnicoService;
import com.distrischool.gestao_administrativa.turma.TurmaController;
import com.distrischool.gestao_administrativa.turma.TurmaService;
import com.distrischool.gestao_administrativa.turma.Turno;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parâmetro {@code busca} nas listagens deste serviço: repasse ao service, combinação com os filtros e limite. */
@WebMvcTest({ProfessorController.class, TecnicoController.class, DisciplinaController.class, TurmaController.class})
class BuscaControllersTest {

    private static final String LONGA = "a".repeat(BuscaTexto.TAMANHO_MAXIMO + 1);
    private static final String NO_LIMITE = "a".repeat(BuscaTexto.TAMANHO_MAXIMO);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfessorService professorService;
    @MockitoBean
    private TecnicoService tecnicoService;
    @MockitoBean
    private DisciplinaService disciplinaService;
    @MockitoBean
    private TurmaService turmaService;

    @Test
    void professoresRepassaBuscaComFiltroNome() throws Exception {
        when(professorService.buscar(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/professores").param("nome", "maria").param("busca", "matemática"))
                .andExpect(status().isOk());

        verify(professorService).buscar(eq("maria"), eq("matemática"), any(Pageable.class));
    }

    @Test
    void tecnicosRepassaBuscaComCargoEAtivo() throws Exception {
        when(tecnicoService.buscar(any(), any(), any(), anyBoolean(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/tecnicos").param("cargo", "SECRETARIO").param("ativo", "false").param("busca", "secretaria"))
                .andExpect(status().isOk());

        verify(tecnicoService).buscar(isNull(), eq(Cargo.SECRETARIO), isNull(), eq(false), eq("secretaria"), any(Pageable.class));
    }

    @Test
    void disciplinasRepassaBuscaComAtiva() throws Exception {
        when(disciplinaService.buscar(any(), anyBoolean(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/disciplinas").param("ativa", "false").param("busca", "fisica"))
                .andExpect(status().isOk());

        verify(disciplinaService).buscar(isNull(), eq(false), eq("fisica"), any(Pageable.class));
    }

    @Test
    void turmasRepassaBuscaComAnoETurno() throws Exception {
        when(turmaService.buscar(any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/turmas").param("anoLetivo", "2026").param("turno", "MANHA").param("busca", "9º ano"))
                .andExpect(status().isOk());

        verify(turmaService).buscar(eq(2026), eq(Turno.MANHA), isNull(), eq("9º ano"), any(Pageable.class));
    }

    @Test
    void semBuscaRepassaNulo() throws Exception {
        when(professorService.buscar(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/professores")).andExpect(status().isOk());

        verify(professorService).buscar(isNull(), isNull(), any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/professores", "/api/tecnicos", "/api/disciplinas", "/api/turmas"})
    void buscaAcimaDoLimiteRetorna400SemConsultar(String rota) throws Exception {
        mockMvc.perform(get(rota).param("busca", LONGA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Dados inválidos"))
                .andExpect(jsonPath("$.erros.busca").value(BuscaTexto.MENSAGEM_TAMANHO));

        verifyNoInteractions(professorService, tecnicoService, disciplinaService, turmaService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/professores", "/api/tecnicos", "/api/disciplinas", "/api/turmas"})
    void buscaNoLimiteEAceita(String rota) throws Exception {
        when(professorService.buscar(any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        when(tecnicoService.buscar(any(), any(), any(), anyBoolean(), any(), any(Pageable.class))).thenReturn(Page.empty());
        when(disciplinaService.buscar(any(), anyBoolean(), any(), any(Pageable.class))).thenReturn(Page.empty());
        when(turmaService.buscar(any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get(rota).param("busca", NO_LIMITE)).andExpect(status().isOk());
    }
}
