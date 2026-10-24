package com.distrischool.gestao_escolar.turma;

import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.common.ServicoIndisponivelException;
import com.distrischool.gestao_escolar.turma.ScheduleUpdatedMessage.Acao;
import com.distrischool.gestao_escolar.turma.ScheduleUpdatedMessage.DisciplinaOfertada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Consumidor do schedule.updated (idempotência e ordem dos eventos) e fallback síncrono; sem
 * Spring, com a réplica guardada em memória.
 */
class TurmaReplicaServiceTest {

    private static final Instant T1 = Instant.parse("2026-10-24T11:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-24T11:05:00Z");
    private static final Instant T3 = Instant.parse("2026-10-24T11:10:00Z");

    private static final DisciplinaOfertada MAT = new DisciplinaOfertada(3L, "MAT", "Matemática", 7L);
    private static final DisciplinaOfertada POR = new DisciplinaOfertada(4L, "POR", "Português", null);

    private final Map<Long, TurmaReplica> replica = new HashMap<>();
    private AdministrativaClient administrativaClient;
    private TurmaReplicaService service;

    @BeforeEach
    void preparar() {
        TurmaReplicaRepository repository = mock(TurmaReplicaRepository.class);
        when(repository.findByIdParaAtualizacao(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(replica.get(inv.<Long>getArgument(0))));
        when(repository.existsById(anyLong())).thenAnswer(inv -> replica.containsKey(inv.<Long>getArgument(0)));
        when(repository.save(any())).thenAnswer(inv -> salvar(inv.getArgument(0)));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> salvar(inv.getArgument(0)));

        administrativaClient = mock(AdministrativaClient.class);
        service = new TurmaReplicaService(repository, administrativaClient, mock(PlatformTransactionManager.class));
    }

    private TurmaReplica salvar(TurmaReplica turma) {
        turma.marcarComoExistente();
        replica.put(turma.getId(), turma);
        return turma;
    }

    // --- consumidor ---

    @Test
    void criadaInsereATurmaEAsOfertas() {
        assertThat(service.aplicar(evento(Acao.CRIADA, T1, 35, List.of(MAT, POR)))).isTrue();

        TurmaReplica turma = replica.get(12L);
        assertThat(turma.getCodigo()).isEqualTo("9A-2026");
        assertThat(turma.getNome()).isEqualTo("9º Ano A");
        assertThat(turma.getAnoLetivo()).isEqualTo(2026);
        assertThat(turma.getTurno()).isEqualTo("MANHA");
        assertThat(turma.getCapacidade()).isEqualTo(35);
        assertThat(turma.isRemovida()).isFalse();
        assertThat(turma.getUltimoEventoEm()).isEqualTo(T1);
        assertThat(turma.getDisciplinas()).hasSize(2);
        assertThat(turma.disciplina(3L)).get().satisfies(d -> {
            assertThat(d.getCodigo()).isEqualTo("MAT");
            assertThat(d.getProfessorId()).isEqualTo(7L);
            assertThat(d.isRemovida()).isFalse();
        });
    }

    @Test
    void reentregaDoMesmoEventoNaoDuplicaNada() {
        ScheduleUpdatedMessage evento = evento(Acao.CRIADA, T1, 35, List.of(MAT));

        service.aplicar(evento);
        assertThat(service.aplicar(evento)).isTrue();

        assertThat(replica).hasSize(1);
        assertThat(replica.get(12L).getDisciplinas()).hasSize(1);
    }

    @Test
    void occurredAtEGravadoComAPrecisaoDoBancoParaAReentregaNaoParecerMaisAntiga() {
        Instant comNanos = Instant.parse("2026-10-24T13:00:08.000219900Z");

        service.aplicar(evento(Acao.CRIADA, comNanos, 35, List.of(MAT)));
        // Simula o valor relido do TIMESTAMPTZ, que tem só microssegundos
        assertThat(replica.get(12L).getUltimoEventoEm()).isEqualTo(Instant.parse("2026-10-24T13:00:08.000219Z"));

        assertThat(service.aplicar(evento(Acao.CRIADA, comNanos, 35, List.of(MAT)))).isTrue();
    }

    @Test
    void eventoMaisAntigoQueOUltimoAplicadoEIgnorado() {
        service.aplicar(evento(Acao.ATUALIZADA, T2, 40, List.of(MAT, POR)));

        boolean aplicado = service.aplicar(evento(Acao.CRIADA, T1, 35, List.of(MAT)));

        assertThat(aplicado).isFalse();
        TurmaReplica turma = replica.get(12L);
        assertThat(turma.getCapacidade()).isEqualTo(40);
        assertThat(turma.getUltimoEventoEm()).isEqualTo(T2);
        assertThat(turma.ofertaDisciplina(4L)).isTrue();
    }

    @Test
    void eventosForaDeOrdemConvergemParaOMaisRecente() {
        service.aplicar(evento(Acao.ATUALIZADA, T3, 30, List.of(POR)));
        service.aplicar(evento(Acao.CRIADA, T1, 35, List.of(MAT)));
        service.aplicar(evento(Acao.ATUALIZADA, T2, 40, List.of(MAT, POR)));

        TurmaReplica turma = replica.get(12L);
        assertThat(turma.getCapacidade()).isEqualTo(30);
        assertThat(turma.getUltimoEventoEm()).isEqualTo(T3);
        assertThat(turma.ofertaDisciplina(4L)).isTrue();
        assertThat(turma.disciplina(3L)).isEmpty();
    }

    @Test
    void ofertaAusenteDaListaEMarcadaComoRemovidaSemSerApagada() {
        service.aplicar(evento(Acao.CRIADA, T1, 35, List.of(MAT, POR)));

        service.aplicar(evento(Acao.ATUALIZADA, T2, 35, List.of(POR)));

        TurmaReplica turma = replica.get(12L);
        assertThat(turma.getDisciplinas()).hasSize(2);
        assertThat(turma.disciplina(3L)).get().extracting(TurmaDisciplinaReplica::isRemovida).isEqualTo(true);
        assertThat(turma.ofertaDisciplina(3L)).isFalse();
        assertThat(turma.ofertaDisciplina(4L)).isTrue();
    }

    @Test
    void ofertaRetiradaEOfertadaDeNovoVoltaAFicarAtiva() {
        service.aplicar(evento(Acao.CRIADA, T1, 35, List.of(MAT)));
        service.aplicar(evento(Acao.ATUALIZADA, T2, 35, List.of()));

        service.aplicar(evento(Acao.ATUALIZADA, T3, 35,
                List.of(new DisciplinaOfertada(3L, "MAT", "Matemática", 8L))));

        TurmaReplica turma = replica.get(12L);
        assertThat(turma.getDisciplinas()).hasSize(1);
        assertThat(turma.ofertaDisciplina(3L)).isTrue();
        assertThat(turma.disciplina(3L)).get().extracting(TurmaDisciplinaReplica::getProfessorId).isEqualTo(8L);
    }

    @Test
    void atualizadaCopiaNomeCodigoECapacidadeNula() {
        service.aplicar(evento(Acao.CRIADA, T1, 35, List.of(MAT)));

        service.aplicar(new ScheduleUpdatedMessage(12L, "9A-2026-N", "9º Ano A (Noite)", 2026, "NOITE", null,
                List.of(new DisciplinaOfertada(3L, "MTB", "Matemática Básica", null)), Acao.ATUALIZADA, T2));

        TurmaReplica turma = replica.get(12L);
        assertThat(turma.getCodigo()).isEqualTo("9A-2026-N");
        assertThat(turma.getTurno()).isEqualTo("NOITE");
        assertThat(turma.getCapacidade()).isNull();
        assertThat(turma.disciplina(3L)).get().satisfies(d -> {
            assertThat(d.getCodigo()).isEqualTo("MTB");
            assertThat(d.getNome()).isEqualTo("Matemática Básica");
            assertThat(d.getProfessorId()).isNull();
        });
    }

    @Test
    void removidaMarcaATurmaSemApagar() {
        service.aplicar(evento(Acao.CRIADA, T1, 35, List.of(MAT)));

        service.aplicar(evento(Acao.REMOVIDA, T2, 35, List.of(MAT)));

        TurmaReplica turma = replica.get(12L);
        assertThat(turma.isRemovida()).isTrue();
        assertThat(turma.getDisciplinas()).hasSize(1);
    }

    @Test
    void criadaAtrasadaNaoRessuscitaTurmaRemovida() {
        service.aplicar(evento(Acao.REMOVIDA, T2, 35, List.of(MAT)));

        service.aplicar(evento(Acao.CRIADA, T1, 35, List.of(MAT)));

        assertThat(replica.get(12L).isRemovida()).isTrue();
    }

    @Test
    void eventoSemCampoObrigatorioEInvalido() {
        assertThatThrownBy(() -> service.aplicar(evento(Acao.CRIADA, null, 35, List.of(MAT))))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> service.aplicar(new ScheduleUpdatedMessage(
                null, "9A-2026", "9º Ano A", 2026, "MANHA", 35, List.of(), Acao.CRIADA, T1)))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> service.aplicar(evento(Acao.CRIADA, T1, 35, null)))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> service.aplicar(evento(Acao.CRIADA, T1, 35,
                List.of(new DisciplinaOfertada(null, "MAT", "Matemática", null)))))
                .isInstanceOf(MensagemInvalidaException.class);

        assertThat(replica).isEmpty();
    }

    // --- fallback síncrono ---

    @Test
    void turmaJaNaReplicaNaoConsultaOOutroServico() {
        service.aplicar(evento(Acao.CRIADA, T1, 35, List.of(MAT)));

        service.garantirNaReplica(12L);

        verify(administrativaClient, never()).buscarTurma(any());
    }

    @Test
    void turmaAusenteEBuscadaEGravadaSemUltimoEvento() {
        when(administrativaClient.buscarTurma(12L)).thenReturn(Optional.of(new TurmaAdministrativaResponse(
                12L, "9A-2026", "9º Ano A", 2026, "MANHA", 35, List.of(MAT))));

        service.garantirNaReplica(12L);

        TurmaReplica turma = replica.get(12L);
        assertThat(turma.getCodigo()).isEqualTo("9A-2026");
        assertThat(turma.getUltimoEventoEm()).isNull();
        assertThat(turma.ofertaDisciplina(3L)).isTrue();
    }

    @Test
    void qualquerEventoPrevaleceSobreOFallback() {
        when(administrativaClient.buscarTurma(12L)).thenReturn(Optional.of(new TurmaAdministrativaResponse(
                12L, "9A-2026", "9º Ano A", 2026, "MANHA", 35, List.of(MAT))));
        service.garantirNaReplica(12L);

        assertThat(service.aplicar(evento(Acao.ATUALIZADA, T1, 20, List.of(POR)))).isTrue();

        assertThat(replica.get(12L).getCapacidade()).isEqualTo(20);
        assertThat(replica.get(12L).getUltimoEventoEm()).isEqualTo(T1);
    }

    @Test
    void turmaInexistenteNoOutroServicoRetorna404() {
        when(administrativaClient.buscarTurma(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.garantirNaReplica(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(replica).isEmpty();
    }

    @Test
    void outroServicoForaDoArPropagaIndisponivel() {
        when(administrativaClient.buscarTurma(12L))
                .thenThrow(new ServicoIndisponivelException("fora do ar", null));

        assertThatThrownBy(() -> service.garantirNaReplica(12L))
                .isInstanceOf(ServicoIndisponivelException.class);
    }

    private static ScheduleUpdatedMessage evento(Acao acao, Instant occurredAt, Integer capacidade,
                                                 List<DisciplinaOfertada> disciplinas) {
        return new ScheduleUpdatedMessage(12L, "9A-2026", "9º Ano A", 2026, "MANHA", capacidade,
                disciplinas, acao, occurredAt);
    }
}
