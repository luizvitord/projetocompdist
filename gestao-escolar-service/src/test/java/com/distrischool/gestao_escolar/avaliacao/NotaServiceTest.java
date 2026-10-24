package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.common.RegraNegocioException;
import com.distrischool.gestao_escolar.common.ValidacaoException;
import com.distrischool.gestao_escolar.event.GradePostedEvent;
import com.distrischool.gestao_escolar.matricula.Matricula;
import com.distrischool.gestao_escolar.matricula.MatriculaRepository;
import com.distrischool.gestao_escolar.matricula.StatusMatricula;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lançamento e correção de notas; sem Spring, com repositórios simulados. */
class NotaServiceTest {

    private NotaRepository notaRepository;
    private NotaHistoricoRepository historicoRepository;
    private MatriculaRepository matriculaRepository;
    private ApplicationEventPublisher eventPublisher;
    private NotaService service;

    private Avaliacao avaliacao;

    @BeforeEach
    void preparar() {
        notaRepository = mock(NotaRepository.class);
        historicoRepository = mock(NotaHistoricoRepository.class);
        AvaliacaoService avaliacaoService = mock(AvaliacaoService.class);
        matriculaRepository = mock(MatriculaRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new NotaService(notaRepository, historicoRepository, avaliacaoService, matriculaRepository, eventPublisher);

        avaliacao = new Avaliacao();
        avaliacao.setId(9L);
        avaliacao.setTurmaId(12L);
        avaliacao.setDisciplinaId(3L);
        avaliacao.setBimestre(2);
        avaliacao.setTipo(TipoAvaliacao.PROVA);
        avaliacao.setPeso(BigDecimal.ONE);
        avaliacao.setNotaMaxima(new BigDecimal("10.00"));
        when(avaliacaoService.obter(9L)).thenReturn(avaliacao);
        when(avaliacaoService.obter(99L)).thenThrow(new RecursoNaoEncontradoException("Avaliação", 99L));

        when(matriculaRepository.findByTurmaIdAndStatus(12L, StatusMatricula.ATIVA))
                .thenReturn(List.of(matricula(41L, 1L), matricula(42L, 2L)));
        when(notaRepository.findByAvaliacaoIdAndMatriculaIdIn(any(), anyCollection())).thenReturn(List.of());
        doAnswer(inv -> {
            List<Nota> notas = new ArrayList<>(inv.getArgument(0));
            long id = 100;
            for (Nota n : notas) {
                n.setId(id++);
            }
            return notas;
        }).when(notaRepository).saveAllAndFlush(anyList());
    }

    private static Matricula matricula(Long id, Long alunoId) {
        Matricula m = new Matricula();
        m.setId(id);
        m.setAlunoId(alunoId);
        m.setTurmaId(12L);
        m.setStatus(StatusMatricula.ATIVA);
        return m;
    }

    private static LancamentoNotaRequest lancamento(Long matriculaId, String valor) {
        return new LancamentoNotaRequest(matriculaId, new BigDecimal(valor));
    }

    // --- lançamento ---

    @Test
    void lancaNotasDoLoteComDuasCasas() {
        List<NotaResponse> notas = service.lancar(9L, List.of(lancamento(42L, "7"), lancamento(41L, "8.5")));

        assertThat(notas).extracting(NotaResponse::matriculaId).containsExactly(41L, 42L);
        assertThat(notas).extracting(NotaResponse::valor).containsExactly(new BigDecimal("8.50"), new BigDecimal("7.00"));
        assertThat(notas).allSatisfy(n -> assertThat(n.avaliacaoId()).isEqualTo(9L));
    }

    @Test
    void lancamentoPublicaGradePostedComTodasAsNotasPorAluno() {
        service.lancar(9L, List.of(lancamento(42L, "7"), lancamento(41L, "8.5")));

        ArgumentCaptor<GradePostedEvent> captor = ArgumentCaptor.forClass(GradePostedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        GradePostedEvent evento = captor.getValue();
        assertThat(evento.avaliacaoId()).isEqualTo(9L);
        assertThat(evento.turmaId()).isEqualTo(12L);
        assertThat(evento.disciplinaId()).isEqualTo(3L);
        assertThat(evento.bimestre()).isEqualTo(2);
        assertThat(evento.notas()).containsExactly(
                new GradePostedEvent.NotaAluno(1L, new BigDecimal("8.50")),
                new GradePostedEvent.NotaAluno(2L, new BigDecimal("7.00")));
    }

    @Test
    void lancamentoParcialEPermitido() {
        assertThat(service.lancar(9L, List.of(lancamento(41L, "6")))).hasSize(1);
    }

    @Test
    void valorAcimaDaNotaMaximaMatriculaNaoAtivaEDuplicadaRetornam400() {
        assertThatThrownBy(() -> service.lancar(9L, List.of(
                lancamento(41L, "10.5"), lancamento(42L, "5"), lancamento(42L, "6"), lancamento(77L, "5"))))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(((ValidacaoException) ex).getPropriedades())
                        .containsEntry("matriculasComValorAcimaDoMaximo", List.of(41L))
                        .containsEntry("matriculasDuplicadas", List.of(42L))
                        .containsEntry("matriculasNaoAtivasNaTurma", List.of(77L)));
        verify(notaRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void valorIgualANotaMaximaEAceito() {
        assertThat(service.lancar(9L, List.of(lancamento(41L, "10")))).hasSize(1);
    }

    @Test
    void matriculaQueJaTemNotaRetorna409() {
        Nota existente = new Nota();
        existente.setMatriculaId(41L);
        when(notaRepository.findByAvaliacaoIdAndMatriculaIdIn(any(), anyCollection())).thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.lancar(9L, List.of(lancamento(41L, "8"), lancamento(42L, "7"))))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("[41]")
                .hasMessageContaining("PUT /api/notas");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void lancamentoConcorrenteViraConflito() {
        doThrow(new DataIntegrityViolationException("uk_nota_avaliacao_matricula")).when(notaRepository).saveAllAndFlush(anyList());

        assertThatThrownBy(() -> service.lancar(9L, List.of(lancamento(41L, "8"))))
                .isInstanceOf(RegraNegocioException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void avaliacaoInexistenteRetorna404() {
        assertThatThrownBy(() -> service.lancar(99L, List.of(lancamento(41L, "8"))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // --- correção ---

    private Nota notaExistente(String valor) {
        Nota nota = new Nota();
        nota.setId(100L);
        nota.setAvaliacao(avaliacao);
        nota.setMatriculaId(41L);
        nota.setValor(new BigDecimal(valor));
        when(notaRepository.findByIdParaAtualizacao(100L)).thenReturn(Optional.of(nota));
        when(matriculaRepository.findById(41L)).thenReturn(Optional.of(matricula(41L, 1L)));
        return nota;
    }

    @Test
    void correcaoGravaOHistoricoEAtualizaANota() {
        Nota nota = notaExistente("6.00");

        NotaResponse resposta = service.corrigir(100L, new CorrecaoNotaRequest(new BigDecimal("7.5"), "  Erro de soma  "));

        assertThat(resposta.valor()).isEqualByComparingTo("7.50");
        assertThat(nota.getValor()).isEqualByComparingTo("7.50");
        ArgumentCaptor<NotaHistorico> captor = ArgumentCaptor.forClass(NotaHistorico.class);
        verify(historicoRepository).save(captor.capture());
        NotaHistorico historico = captor.getValue();
        assertThat(historico.getNotaId()).isEqualTo(100L);
        assertThat(historico.getValorAnterior()).isEqualByComparingTo("6.00");
        assertThat(historico.getValorNovo()).isEqualByComparingTo("7.50");
        assertThat(historico.getMotivo()).isEqualTo("Erro de soma");
    }

    @Test
    void correcaoPublicaGradePostedComANota() {
        notaExistente("6.00");

        service.corrigir(100L, new CorrecaoNotaRequest(new BigDecimal("7.5"), "Erro de soma"));

        ArgumentCaptor<GradePostedEvent> captor = ArgumentCaptor.forClass(GradePostedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().notas()).containsExactly(new GradePostedEvent.NotaAluno(1L, new BigDecimal("7.50")));
    }

    @Test
    void correcaoAcimaDaNotaMaximaRetorna400() {
        notaExistente("6.00");

        assertThatThrownBy(() -> service.corrigir(100L, new CorrecaoNotaRequest(new BigDecimal("10.01"), "x")))
                .isInstanceOf(ValidacaoException.class);
        verify(historicoRepository, never()).save(any());
    }

    @Test
    void correcaoParaOMesmoValorRetorna409SemHistorico() {
        notaExistente("6.00");

        assertThatThrownBy(() -> service.corrigir(100L, new CorrecaoNotaRequest(new BigDecimal("6"), "x")))
                .isInstanceOf(RegraNegocioException.class);
        verify(historicoRepository, never()).save(any());
    }

    @Test
    void correcaoDeNotaInexistenteRetorna404() {
        when(notaRepository.findByIdParaAtualizacao(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.corrigir(5L, new CorrecaoNotaRequest(BigDecimal.ONE, "x")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void historicoDeNotaInexistenteRetorna404() {
        assertThatThrownBy(() -> service.historico(5L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
