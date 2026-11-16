package com.distrischool.gestao_escolar.common;

import com.distrischool.gestao_escolar.aluno.AlunoController;
import com.distrischool.gestao_escolar.aluno.AlunoService;
import com.distrischool.gestao_escolar.avaliacao.AvaliacaoController;
import com.distrischool.gestao_escolar.avaliacao.AvaliacaoService;
import com.distrischool.gestao_escolar.avaliacao.NotaService;
import com.distrischool.gestao_escolar.matricula.MatriculaController;
import com.distrischool.gestao_escolar.matricula.MatriculaService;
import com.distrischool.gestao_escolar.matricula.StatusMatricula;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parâmetro {@code busca} nas listagens deste serviço: repasse ao service, combinação com os filtros e limite. */
@WebMvcTest({AlunoController.class, MatriculaController.class, AvaliacaoController.class})
class BuscaControllersTest {

    private static final String LONGA = "a".repeat(BuscaTexto.TAMANHO_MAXIMO + 1);
    private static final String NO_LIMITE = "a".repeat(BuscaTexto.TAMANHO_MAXIMO);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlunoService alunoService;
    @MockitoBean
    private MatriculaService matriculaService;
    @MockitoBean
    private AvaliacaoService avaliacaoService;
    @MockitoBean
    private NotaService notaService;

    private void respostasVazias() {
        when(alunoService.buscar(any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        when(matriculaService.buscar(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        when(avaliacaoService.buscar(any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());
    }

    @Test
    void alunosRepassaBuscaComFiltroTurma() throws Exception {
        respostasVazias();

        mockMvc.perform(get("/api/alunos").param("turmaId", "12").param("busca", "joão"))
                .andExpect(status().isOk());

        verify(alunoService).buscar(isNull(), isNull(), eq(12L), eq("joão"), any(Pageable.class));
    }

    @Test
    void matriculasRepassaBuscaComAnoEStatus() throws Exception {
        respostasVazias();

        mockMvc.perform(get("/api/matriculas").param("anoLetivo", "2026").param("status", "ATIVA")
                        .param("busca", "9º ano"))
                .andExpect(status().isOk());

        verify(matriculaService).buscar(isNull(), isNull(), eq(2026), eq(StatusMatricula.ATIVA), eq("9º ano"),
                any(Pageable.class));
    }

    @Test
    void avaliacoesRepassaBuscaFiltrosEOrdenacaoPedida() throws Exception {
        respostasVazias();

        mockMvc.perform(get("/api/avaliacoes").param("turmaId", "12").param("disciplinaId", "3")
                        .param("bimestre", "2").param("busca", "avaliacao")
                        .param("sort", "data,desc").param("sort", "id,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(avaliacaoService).buscar(eq(12L), eq(3L), eq(2), eq("avaliacao"), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .containsExactly(Sort.Order.desc("data"), Sort.Order.desc("id"));
    }

    @Test
    void avaliacoesSemSortMantemOPadraoDaApi() throws Exception {
        respostasVazias();

        mockMvc.perform(get("/api/avaliacoes")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(avaliacaoService).buscar(isNull(), isNull(), isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.asc("data"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/alunos", "/api/matriculas", "/api/avaliacoes"})
    void buscaAcimaDoLimiteRetorna400SemConsultar(String rota) throws Exception {
        mockMvc.perform(get(rota).param("busca", LONGA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Dados inválidos"))
                .andExpect(jsonPath("$.erros.busca").value(BuscaTexto.MENSAGEM_TAMANHO));

        verifyNoInteractions(alunoService, matriculaService, avaliacaoService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/alunos", "/api/matriculas", "/api/avaliacoes"})
    void buscaNoLimiteEAceita(String rota) throws Exception {
        respostasVazias();

        mockMvc.perform(get(rota).param("busca", NO_LIMITE)).andExpect(status().isOk());
    }
}
