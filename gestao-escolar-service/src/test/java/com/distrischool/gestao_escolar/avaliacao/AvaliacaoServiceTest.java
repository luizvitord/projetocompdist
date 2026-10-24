package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.common.RegraNegocioException;
import com.distrischool.gestao_escolar.turma.TurmaReplica;
import com.distrischool.gestao_escolar.turma.TurmaReplicaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras de avaliação; sem Spring, com repositórios simulados. */
class AvaliacaoServiceTest {

    private static final LocalDate DATA = LocalDate.of(2026, 5, 10);

    private AvaliacaoRepository avaliacaoRepository;
    private NotaRepository notaRepository;
    private TurmaReplicaService turmaReplicaService;
    private AvaliacaoService service;

    @BeforeEach
    void preparar() {
        avaliacaoRepository = mock(AvaliacaoRepository.class);
        notaRepository = mock(NotaRepository.class);
        turmaReplicaService = mock(TurmaReplicaService.class);
        service = new AvaliacaoService(avaliacaoRepository, notaRepository, turmaReplicaService,
                mock(PlatformTransactionManager.class));

        when(turmaReplicaService.obterComOferta(any(), any())).thenReturn(new TurmaReplica(12L));
        doAnswer(inv -> {
            Avaliacao a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(9L);
            }
            return a;
        }).when(avaliacaoRepository).saveAndFlush(any());
    }

    private static AvaliacaoRequest request(TipoAvaliacao tipo, String peso, String notaMaxima) {
        return new AvaliacaoRequest(12L, 3L, 2, tipo, "Prova bimestral",
                peso == null ? null : new BigDecimal(peso), notaMaxima == null ? null : new BigDecimal(notaMaxima), DATA);
    }

    private Avaliacao existente(String notaMaxima, boolean comNotas) {
        Avaliacao a = new Avaliacao();
        a.setId(9L);
        a.setTurmaId(12L);
        a.setDisciplinaId(3L);
        a.setBimestre(2);
        a.setTipo(TipoAvaliacao.PROVA);
        a.setPeso(BigDecimal.ONE);
        a.setNotaMaxima(new BigDecimal(notaMaxima));
        a.setData(DATA);
        when(avaliacaoRepository.findById(9L)).thenReturn(Optional.of(a));
        when(notaRepository.existsByAvaliacaoId(9L)).thenReturn(comNotas);
        return a;
    }

    @Test
    void pesoENotaMaximaTemPadrao() {
        AvaliacaoResponse resposta = service.criar(request(TipoAvaliacao.PROVA, null, null));

        assertThat(resposta.peso()).isEqualByComparingTo("1");
        assertThat(resposta.notaMaxima()).isEqualByComparingTo("10");
        assertThat(resposta.bimestre()).isEqualTo(2);
    }

    @Test
    void validaTurmaEDisciplinaComoAsChamadas() {
        when(turmaReplicaService.obterComOferta(12L, 3L))
                .thenThrow(new RegraNegocioException("A disciplina 3 não é ofertada na turma 9A"));

        assertThatThrownBy(() -> service.criar(request(TipoAvaliacao.PROVA, null, null)))
                .isInstanceOf(RegraNegocioException.class);
        verify(turmaReplicaService).garantirNaReplica(12L);
        verify(avaliacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void segundaRecuperacaoNoBimestreRetorna409() {
        when(avaliacaoRepository.existsByTurmaIdAndDisciplinaIdAndBimestreAndTipo(12L, 3L, 2, TipoAvaliacao.RECUPERACAO))
                .thenReturn(true);

        assertThatThrownBy(() -> service.criar(request(TipoAvaliacao.RECUPERACAO, null, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("recuperação");
    }

    @Test
    void variasProvasNoMesmoBimestreSaoPermitidas() {
        when(avaliacaoRepository.existsByTurmaIdAndDisciplinaIdAndBimestreAndTipo(any(), any(), any(), any()))
                .thenReturn(true);

        assertThat(service.criar(request(TipoAvaliacao.PROVA, null, null)).id()).isEqualTo(9L);
    }

    @Test
    void recuperacaoCriadaEmParaleloViraConflito() {
        doThrow(new DataIntegrityViolationException("uk_avaliacao_recuperacao")).when(avaliacaoRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.criar(request(TipoAvaliacao.RECUPERACAO, null, null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void comNotasNaoPodeMudarANotaMaxima() {
        existente("10", true);

        assertThatThrownBy(() -> service.atualizar(9L, request(TipoAvaliacao.PROVA, "1", "20")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("nota máxima");
    }

    @Test
    void comNotasPodeMudarPesoEDescricao() {
        existente("10.00", true);

        AvaliacaoResponse resposta = service.atualizar(9L, request(TipoAvaliacao.PROVA, "3", "10"));

        assertThat(resposta.peso()).isEqualByComparingTo("3");
        assertThat(resposta.descricao()).isEqualTo("Prova bimestral");
    }

    @Test
    void semNotasPodeMudarANotaMaxima() {
        existente("10", false);

        assertThat(service.atualizar(9L, request(TipoAvaliacao.PROVA, "1", "20")).notaMaxima()).isEqualByComparingTo("20");
    }

    @Test
    void virarRecuperacaoQuandoJaExisteOutraRetorna409() {
        existente("10", false);
        when(avaliacaoRepository.existsByTurmaIdAndDisciplinaIdAndBimestreAndTipoAndIdNot(12L, 3L, 2, TipoAvaliacao.RECUPERACAO, 9L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(9L, request(TipoAvaliacao.RECUPERACAO, null, null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void excluirComNotasRetorna409() {
        existente("10", true);

        assertThatThrownBy(() -> service.excluir(9L)).isInstanceOf(RegraNegocioException.class);
        verify(avaliacaoRepository, never()).delete(any(Avaliacao.class));
    }

    @Test
    void excluiSemNotas() {
        Avaliacao avaliacao = existente("10", false);

        service.excluir(9L);

        verify(avaliacaoRepository).delete(avaliacao);
    }
}
