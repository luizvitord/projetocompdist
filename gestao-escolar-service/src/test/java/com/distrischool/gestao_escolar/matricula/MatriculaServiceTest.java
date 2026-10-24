package com.distrischool.gestao_escolar.matricula;

import com.distrischool.gestao_escolar.aluno.AlunoService;
import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.common.RegraNegocioException;
import com.distrischool.gestao_escolar.common.ServicoIndisponivelException;
import com.distrischool.gestao_escolar.common.ValidacaoException;
import com.distrischool.gestao_escolar.event.EnrollmentCreatedEvent;
import com.distrischool.gestao_escolar.event.EnrollmentUpdatedEvent;
import com.distrischool.gestao_escolar.turma.TurmaReplica;
import com.distrischool.gestao_escolar.turma.TurmaReplicaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras de matrícula; sem Spring, com as matrículas guardadas em memória. */
class MatriculaServiceTest {

    private final Map<Long, Matricula> matriculas = new HashMap<>();
    private final AtomicLong ids = new AtomicLong(100);

    private MatriculaRepository matriculaRepository;
    private AlunoService alunoService;
    private TurmaReplicaService turmaReplicaService;
    private ApplicationEventPublisher eventPublisher;
    private MatriculaService service;

    private TurmaReplica turma9A;
    private TurmaReplica turma9B;

    @BeforeEach
    void preparar() {
        matriculaRepository = mock(MatriculaRepository.class);
        alunoService = mock(AlunoService.class);
        turmaReplicaService = mock(TurmaReplicaService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new MatriculaService(matriculaRepository, alunoService, turmaReplicaService, eventPublisher,
                mock(PlatformTransactionManager.class));

        when(matriculaRepository.saveAndFlush(any())).thenAnswer(inv -> salvar(inv.getArgument(0)));
        when(matriculaRepository.save(any())).thenAnswer(inv -> salvar(inv.getArgument(0)));
        when(matriculaRepository.findByIdParaAtualizacao(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(matriculas.get(inv.<Long>getArgument(0))));
        when(matriculaRepository.countByTurmaIdAndStatus(anyLong(), any())).thenAnswer(inv -> matriculas.values().stream()
                .filter(m -> m.getTurmaId().equals(inv.getArgument(0)) && m.getStatus() == inv.getArgument(1))
                .count());
        when(matriculaRepository.existsByAlunoIdAndAnoLetivoAndStatusIn(anyLong(), any(), any()))
                .thenAnswer(inv -> matriculas.values().stream().anyMatch(m ->
                        m.getAlunoId().equals(inv.getArgument(0)) && m.getAnoLetivo().equals(inv.getArgument(1))
                                && inv.<java.util.Collection<?>>getArgument(2).contains(m.getStatus())));

        turma9A = turma(12L, "9A-2026", 2026, 2);
        turma9B = turma(15L, "9B-2026", 2026, 30);
    }

    private Matricula salvar(Matricula m) {
        if (m.getId() == null) {
            m.setId(ids.incrementAndGet());
        }
        matriculas.put(m.getId(), m);
        return m;
    }

    private TurmaReplica turma(Long id, String codigo, int anoLetivo, Integer capacidade) {
        TurmaReplica t = new TurmaReplica(id);
        t.setCodigo(codigo);
        t.setNome(codigo);
        t.setAnoLetivo(anoLetivo);
        t.setTurno("MANHA");
        t.setCapacidade(capacidade);
        when(turmaReplicaService.obterComBloqueio(id)).thenReturn(t);
        return t;
    }

    private Matricula existente(Long alunoId, TurmaReplica turma, StatusMatricula status) {
        Matricula m = new Matricula();
        m.setAlunoId(alunoId);
        m.setTurmaId(turma.getId());
        m.setAnoLetivo(turma.getAnoLetivo());
        m.setStatus(status);
        m.setDataMatricula(LocalDate.now().minusMonths(2));
        return salvar(m);
    }

    // --- criação ---

    @Test
    void criaMatriculaAtivaCopiandoOAnoLetivoDaTurma() {
        MatriculaResponse resposta = service.criar(new MatriculaRequest(1L, 12L));

        assertThat(resposta.alunoId()).isEqualTo(1L);
        assertThat(resposta.turmaId()).isEqualTo(12L);
        assertThat(resposta.anoLetivo()).isEqualTo(2026);
        assertThat(resposta.status()).isEqualTo(StatusMatricula.ATIVA);
        assertThat(resposta.dataMatricula()).isEqualTo(LocalDate.now());
        assertThat(resposta.matriculaOrigemId()).isNull();
    }

    @Test
    void criarPublicaEnrollmentCreated() {
        MatriculaResponse resposta = service.criar(new MatriculaRequest(1L, 12L));

        EnrollmentCreatedEvent evento = unico(EnrollmentCreatedEvent.class);
        assertThat(evento.matriculaId()).isEqualTo(resposta.id());
        assertThat(evento.alunoId()).isEqualTo(1L);
        assertThat(evento.turmaId()).isEqualTo(12L);
        assertThat(evento.anoLetivo()).isEqualTo(2026);
        assertThat(evento.occurredAt()).isNotNull();
    }

    @Test
    void garanteATurmaNaReplicaAntesDeBloquear() {
        service.criar(new MatriculaRequest(1L, 12L));

        InOrder ordem = inOrder(turmaReplicaService);
        ordem.verify(turmaReplicaService).garantirNaReplica(12L);
        ordem.verify(turmaReplicaService).obterComBloqueio(12L);
    }

    @Test
    void alunoInexistenteRetorna404() {
        when(alunoService.obter(99L)).thenThrow(new RecursoNaoEncontradoException("Aluno", 99L));

        assertThatThrownBy(() -> service.criar(new MatriculaRequest(99L, 12L)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(turmaReplicaService, never()).garantirNaReplica(any());
    }

    @Test
    void turmaForaDaReplicaComOutroServicoForaDoArRetorna503() {
        doThrow(new ServicoIndisponivelException("fora do ar", null)).when(turmaReplicaService).garantirNaReplica(20L);

        assertThatThrownBy(() -> service.criar(new MatriculaRequest(1L, 20L)))
                .isInstanceOf(ServicoIndisponivelException.class);
        assertThat(matriculas).isEmpty();
    }

    @Test
    void turmaRemovidaNaoAceitaMatricula() {
        turma9A.setRemovida(true);

        assertThatThrownBy(() -> service.criar(new MatriculaRequest(1L, 12L)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("removida");
    }

    @Test
    void alunoComMatriculaAtivaNoAnoNaoPodeSeMatricularDeNovo() {
        existente(1L, turma9B, StatusMatricula.ATIVA);

        assertThatThrownBy(() -> service.criar(new MatriculaRequest(1L, 12L)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("2026");
    }

    @Test
    void alunoComMatriculaTrancadaNoAnoNaoPodeSeMatricularDeNovo() {
        existente(1L, turma9B, StatusMatricula.TRANCADA);

        assertThatThrownBy(() -> service.criar(new MatriculaRequest(1L, 12L)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void matriculaCanceladaNoAnoNaoImpedeNovaMatricula() {
        existente(1L, turma9B, StatusMatricula.CANCELADA);

        assertThat(service.criar(new MatriculaRequest(1L, 12L)).status()).isEqualTo(StatusMatricula.ATIVA);
    }

    @Test
    void turmaLotadaRetorna409() {
        existente(2L, turma9A, StatusMatricula.ATIVA);
        existente(3L, turma9A, StatusMatricula.ATIVA);

        assertThatThrownBy(() -> service.criar(new MatriculaRequest(1L, 12L)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("vagas");
    }

    @Test
    void matriculasTrancadasNaoOcupamVaga() {
        existente(2L, turma9A, StatusMatricula.ATIVA);
        existente(3L, turma9A, StatusMatricula.TRANCADA);

        assertThat(service.criar(new MatriculaRequest(1L, 12L)).status()).isEqualTo(StatusMatricula.ATIVA);
    }

    @Test
    void capacidadeNulaESemLimite() {
        turma9A.setCapacidade(null);
        for (long aluno = 2; aluno < 50; aluno++) {
            existente(aluno, turma9A, StatusMatricula.ATIVA);
        }

        assertThat(service.criar(new MatriculaRequest(1L, 12L)).status()).isEqualTo(StatusMatricula.ATIVA);
    }

    @Test
    void violacaoDoIndiceUnicoEmParaleloViraConflito() {
        doThrow(new DataIntegrityViolationException("uk_matricula_aluno_ano_vigente"))
                .when(matriculaRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.criar(new MatriculaRequest(1L, 12L)))
                .isInstanceOf(RegraNegocioException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // --- status ---

    @Test
    void trancarExigeMotivo() {
        Matricula m = existente(1L, turma9A, StatusMatricula.ATIVA);

        assertThatThrownBy(() -> service.alterarStatus(m.getId(), new AlteracaoStatusRequest(StatusMatricula.TRANCADA, "  ")))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(((ValidacaoException) ex).getPropriedades()).containsKey("erros"));
        assertThat(m.getStatus()).isEqualTo(StatusMatricula.ATIVA);
    }

    @Test
    void concluirNaoExigeMotivoEEncerraAMatricula() {
        Matricula m = existente(1L, turma9A, StatusMatricula.ATIVA);

        MatriculaResponse resposta = service.alterarStatus(m.getId(), new AlteracaoStatusRequest(StatusMatricula.CONCLUIDA, null));

        assertThat(resposta.status()).isEqualTo(StatusMatricula.CONCLUIDA);
        assertThat(resposta.dataEncerramento()).isEqualTo(LocalDate.now());
    }

    @Test
    void trancarGuardaOMotivoSemEncerrar() {
        Matricula m = existente(1L, turma9A, StatusMatricula.ATIVA);

        MatriculaResponse resposta = service.alterarStatus(m.getId(),
                new AlteracaoStatusRequest(StatusMatricula.TRANCADA, " Tratamento de saúde "));

        assertThat(resposta.status()).isEqualTo(StatusMatricula.TRANCADA);
        assertThat(resposta.motivo()).isEqualTo("Tratamento de saúde");
        assertThat(resposta.dataEncerramento()).isNull();
    }

    @Test
    void transicaoInvalidaRetorna409() {
        Matricula m = existente(1L, turma9A, StatusMatricula.CANCELADA);

        assertThatThrownBy(() -> service.alterarStatus(m.getId(), new AlteracaoStatusRequest(StatusMatricula.ATIVA, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CANCELADA → ATIVA");
    }

    @Test
    void matriculaInexistenteRetorna404() {
        assertThatThrownBy(() -> service.alterarStatus(999L, new AlteracaoStatusRequest(StatusMatricula.CONCLUIDA, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void reativarMatriculaTrancadaVerificaAVaga() {
        existente(2L, turma9A, StatusMatricula.ATIVA);
        existente(3L, turma9A, StatusMatricula.ATIVA);
        Matricula trancada = existente(1L, turma9A, StatusMatricula.TRANCADA);

        assertThatThrownBy(() -> service.alterarStatus(trancada.getId(), new AlteracaoStatusRequest(StatusMatricula.ATIVA, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("vagas");
    }

    @Test
    void reativarMatriculaTrancadaComVaga() {
        Matricula trancada = existente(1L, turma9A, StatusMatricula.TRANCADA);

        assertThat(service.alterarStatus(trancada.getId(), new AlteracaoStatusRequest(StatusMatricula.ATIVA, null)).status())
                .isEqualTo(StatusMatricula.ATIVA);
    }

    @Test
    void alterarStatusPublicaEnrollmentUpdatedSemTurmaDestino() {
        Matricula m = existente(1L, turma9A, StatusMatricula.ATIVA);

        service.alterarStatus(m.getId(), new AlteracaoStatusRequest(StatusMatricula.CANCELADA, "Desistência"));

        EnrollmentUpdatedEvent evento = unico(EnrollmentUpdatedEvent.class);
        assertThat(evento.matriculaId()).isEqualTo(m.getId());
        assertThat(evento.alunoId()).isEqualTo(1L);
        assertThat(evento.turmaId()).isEqualTo(12L);
        assertThat(evento.statusAnterior()).isEqualTo("ATIVA");
        assertThat(evento.statusNovo()).isEqualTo("CANCELADA");
        assertThat(evento.turmaDestinoId()).isNull();
    }

    // --- transferência ---

    @Test
    void transferenciaEncerraAOrigemECriaNovaMatriculaNoDestino() {
        Matricula origem = existente(1L, turma9A, StatusMatricula.ATIVA);

        MatriculaResponse nova = service.transferir(origem.getId(), new TransferenciaRequest(15L, "Mudança de turno"));

        assertThat(origem.getStatus()).isEqualTo(StatusMatricula.TRANSFERIDA);
        assertThat(origem.getMotivo()).isEqualTo("Mudança de turno");
        assertThat(origem.getDataEncerramento()).isEqualTo(LocalDate.now());
        assertThat(nova.id()).isNotEqualTo(origem.getId());
        assertThat(nova.turmaId()).isEqualTo(15L);
        assertThat(nova.status()).isEqualTo(StatusMatricula.ATIVA);
        assertThat(nova.matriculaOrigemId()).isEqualTo(origem.getId());
        assertThat(nova.anoLetivo()).isEqualTo(2026);
    }

    @Test
    void transferenciaGravaAOrigemAntesDeCriarANova() {
        Matricula origem = existente(1L, turma9A, StatusMatricula.ATIVA);
        List<StatusMatricula> statusGravados = new ArrayList<>();
        doAnswer(inv -> {
            Matricula m = inv.getArgument(0);
            statusGravados.add(m.getStatus());
            return salvar(m);
        }).when(matriculaRepository).saveAndFlush(any());

        service.transferir(origem.getId(), new TransferenciaRequest(15L, "Mudança de turno"));

        // O índice único (aluno, ano) só aceita a nova ATIVA depois que a origem deixou de ser ATIVA
        assertThat(statusGravados).containsExactly(StatusMatricula.TRANSFERIDA, StatusMatricula.ATIVA);
    }

    @Test
    void transferenciaPublicaUpdatedComTurmaDestinoECreated() {
        Matricula origem = existente(1L, turma9A, StatusMatricula.ATIVA);

        MatriculaResponse nova = service.transferir(origem.getId(), new TransferenciaRequest(15L, "Mudança de turno"));

        EnrollmentUpdatedEvent atualizada = unico(EnrollmentUpdatedEvent.class);
        assertThat(atualizada.matriculaId()).isEqualTo(origem.getId());
        assertThat(atualizada.turmaId()).isEqualTo(12L);
        assertThat(atualizada.statusAnterior()).isEqualTo("ATIVA");
        assertThat(atualizada.statusNovo()).isEqualTo("TRANSFERIDA");
        assertThat(atualizada.turmaDestinoId()).isEqualTo(15L);
        EnrollmentCreatedEvent criada = unico(EnrollmentCreatedEvent.class);
        assertThat(criada.matriculaId()).isEqualTo(nova.id());
        assertThat(criada.turmaId()).isEqualTo(15L);
    }

    @Test
    void transferenciaParaOutroAnoLetivoRetorna409() {
        turma(16L, "1A-2027", 2027, 30);
        Matricula origem = existente(1L, turma9A, StatusMatricula.ATIVA);

        assertThatThrownBy(() -> service.transferir(origem.getId(), new TransferenciaRequest(16L, "Mudança")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("ano letivo");
        assertThat(origem.getStatus()).isEqualTo(StatusMatricula.ATIVA);
    }

    @Test
    void transferenciaParaAMesmaTurmaRetorna409() {
        Matricula origem = existente(1L, turma9A, StatusMatricula.ATIVA);

        assertThatThrownBy(() -> service.transferir(origem.getId(), new TransferenciaRequest(12L, "Mudança")))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void transferenciaParaTurmaLotadaRetorna409() {
        turma9B.setCapacidade(1);
        existente(2L, turma9B, StatusMatricula.ATIVA);
        Matricula origem = existente(1L, turma9A, StatusMatricula.ATIVA);

        assertThatThrownBy(() -> service.transferir(origem.getId(), new TransferenciaRequest(15L, "Mudança")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("vagas");
        assertThat(origem.getStatus()).isEqualTo(StatusMatricula.ATIVA);
    }

    @Test
    void transferenciaParaTurmaRemovidaRetorna409() {
        turma9B.setRemovida(true);
        Matricula origem = existente(1L, turma9A, StatusMatricula.ATIVA);

        assertThatThrownBy(() -> service.transferir(origem.getId(), new TransferenciaRequest(15L, "Mudança")))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void matriculaTrancadaNaoPodeSerTransferida() {
        Matricula origem = existente(1L, turma9A, StatusMatricula.TRANCADA);

        assertThatThrownBy(() -> service.transferir(origem.getId(), new TransferenciaRequest(15L, "Mudança")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("TRANCADA → TRANSFERIDA");
    }

    private <T> T unico(Class<T> tipo) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeast(0)).publishEvent(captor.capture());
        List<T> eventos = captor.getAllValues().stream().filter(tipo::isInstance).map(tipo::cast).toList();
        assertThat(eventos).hasSize(1);
        return eventos.get(0);
    }
}
