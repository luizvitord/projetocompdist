package com.distrischool.gestao_escolar.frequencia;

import com.distrischool.gestao_escolar.common.RegraNegocioException;
import com.distrischool.gestao_escolar.common.ValidacaoException;
import com.distrischool.gestao_escolar.event.AttendanceRecordedEvent;
import com.distrischool.gestao_escolar.matricula.Matricula;
import com.distrischool.gestao_escolar.matricula.MatriculaRepository;
import com.distrischool.gestao_escolar.matricula.StatusMatricula;
import com.distrischool.gestao_escolar.turma.TurmaReplica;
import com.distrischool.gestao_escolar.turma.TurmaReplicaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.distrischool.gestao_escolar.frequencia.StatusPresenca.ATRASO;
import static com.distrischool.gestao_escolar.frequencia.StatusPresenca.FALTA;
import static com.distrischool.gestao_escolar.frequencia.StatusPresenca.PRESENTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras da chamada; sem Spring, com repositórios simulados. */
class ChamadaServiceTest {

    private static final LocalDate ONTEM = LocalDate.now().minusDays(1);

    private ChamadaRepository chamadaRepository;
    private MatriculaRepository matriculaRepository;
    private TurmaReplicaService turmaReplicaService;
    private ApplicationEventPublisher eventPublisher;
    private ChamadaService service;

    private final List<Matricula> ativas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        chamadaRepository = mock(ChamadaRepository.class);
        matriculaRepository = mock(MatriculaRepository.class);
        turmaReplicaService = mock(TurmaReplicaService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new ChamadaService(chamadaRepository, matriculaRepository, turmaReplicaService, eventPublisher,
                mock(PlatformTransactionManager.class));

        TurmaReplica turma = new TurmaReplica(12L);
        turma.setCodigo("9A");
        turma.setAnoLetivo(ONTEM.getYear());
        when(turmaReplicaService.obterComOferta(12L, 3L)).thenReturn(turma);
        when(matriculaRepository.findByTurmaIdAndStatus(12L, StatusMatricula.ATIVA)).thenReturn(ativas);
        ativas.add(matricula(41L, 1L));
        ativas.add(matricula(42L, 2L));
        ativas.add(matricula(43L, 3L));

        doAnswer(inv -> {
            Chamada c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(500L);
            }
            long id = 1;
            for (RegistroFrequencia r : c.getRegistros()) {
                if (r.getId() == null) {
                    r.setId(1000 + id++);
                }
            }
            return c;
        }).when(chamadaRepository).saveAndFlush(any());
    }

    private static Matricula matricula(Long id, Long alunoId) {
        Matricula m = new Matricula();
        m.setId(id);
        m.setAlunoId(alunoId);
        m.setTurmaId(12L);
        m.setStatus(StatusMatricula.ATIVA);
        return m;
    }

    private static ChamadaRequest.Registro r(Long matriculaId, StatusPresenca status) {
        return new ChamadaRequest.Registro(matriculaId, status, null);
    }

    private static ChamadaRequest request(LocalDate data, ChamadaRequest.Registro... registros) {
        return new ChamadaRequest(12L, 3L, data, 7L, List.of(registros));
    }

    @Test
    void registraAChamadaComTodasAsMatriculasAtivas() {
        ChamadaResponse resposta = service.registrar(request(ONTEM, r(41L, PRESENTE), r(42L, FALTA), r(43L, ATRASO)));

        assertThat(resposta.id()).isEqualTo(500L);
        assertThat(resposta.data()).isEqualTo(ONTEM);
        assertThat(resposta.professorId()).isEqualTo(7L);
        assertThat(resposta.registros()).extracting(ChamadaResponse.Registro::matriculaId, ChamadaResponse.Registro::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(41L, PRESENTE),
                        org.assertj.core.groups.Tuple.tuple(42L, FALTA),
                        org.assertj.core.groups.Tuple.tuple(43L, ATRASO));
    }

    @Test
    void garanteATurmaNaReplicaAntes() {
        service.registrar(request(ONTEM, r(41L, PRESENTE), r(42L, PRESENTE), r(43L, PRESENTE)));

        verify(turmaReplicaService).garantirNaReplica(12L);
    }

    @Test
    void publicaAttendanceRecordedSoComFaltasEAtrasosPorAluno() {
        service.registrar(request(ONTEM, r(41L, PRESENTE), r(42L, FALTA), r(43L, ATRASO)));

        ArgumentCaptor<AttendanceRecordedEvent> captor = ArgumentCaptor.forClass(AttendanceRecordedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AttendanceRecordedEvent evento = captor.getValue();
        assertThat(evento.chamadaId()).isEqualTo(500L);
        assertThat(evento.turmaId()).isEqualTo(12L);
        assertThat(evento.disciplinaId()).isEqualTo(3L);
        assertThat(evento.data()).isEqualTo(ONTEM);
        assertThat(evento.ausencias()).containsExactly(
                new AttendanceRecordedEvent.Ausencia(2L, "FALTA"),
                new AttendanceRecordedEvent.Ausencia(3L, "ATRASO"));
    }

    @Test
    void matriculaFaltandoSobrandoOuRepetidaRetorna400ComAsDivergentes() {
        assertThatThrownBy(() -> service.registrar(request(ONTEM, r(41L, PRESENTE), r(41L, FALTA), r(99L, PRESENTE))))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(((ValidacaoException) ex).getPropriedades())
                        .containsEntry("matriculasFaltando", List.of(42L, 43L))
                        .containsEntry("matriculasNaoEsperadas", List.of(99L))
                        .containsEntry("matriculasDuplicadas", List.of(41L)));
        verify(chamadaRepository, never()).saveAndFlush(any());
    }

    @Test
    void matriculaNaoAtivaNaoPodeConstarNaChamada() {
        ativas.remove(2);

        assertThatThrownBy(() -> service.registrar(request(ONTEM, r(41L, PRESENTE), r(42L, PRESENTE), r(43L, PRESENTE))))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(((ValidacaoException) ex).getPropriedades())
                        .containsEntry("matriculasNaoEsperadas", List.of(43L)));
    }

    @Test
    void dataFuturaRetorna400() {
        assertThatThrownBy(() -> service.registrar(request(LocalDate.now().plusDays(1), r(41L, PRESENTE))))
                .isInstanceOf(ValidacaoException.class);
    }

    @Test
    void dataForaDoAnoLetivoDaTurmaRetorna400() {
        assertThatThrownBy(() -> service.registrar(request(ONTEM.minusYears(1), r(41L, PRESENTE), r(42L, PRESENTE), r(43L, PRESENTE))))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(((ValidacaoException) ex).getPropriedades().get("erros").toString()).contains("data"));
    }

    @Test
    void turmaRemovidaOuDisciplinaNaoOfertadaPropagam409() {
        when(turmaReplicaService.obterComOferta(12L, 3L))
                .thenThrow(new RegraNegocioException("A disciplina 3 não é ofertada na turma 9A"));

        assertThatThrownBy(() -> service.registrar(request(ONTEM, r(41L, PRESENTE))))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void chamadaRepetidaNaMesmaDataRetorna409() {
        when(chamadaRepository.existsByTurmaIdAndDisciplinaIdAndData(12L, 3L, ONTEM)).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(request(ONTEM, r(41L, PRESENTE), r(42L, PRESENTE), r(43L, PRESENTE))))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Já existe uma chamada");
    }

    @Test
    void chamadaGravadaEmParaleloRetorna409() {
        doThrow(new DataIntegrityViolationException("uk_chamada_turma_disciplina_data"))
                .when(chamadaRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.registrar(request(ONTEM, r(41L, PRESENTE), r(42L, PRESENTE), r(43L, PRESENTE))))
                .isInstanceOf(RegraNegocioException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // --- edição ---

    @Test
    void edicaoAtualizaOsRegistrosNoLugar() {
        Chamada existente = existente(r(41L, PRESENTE), r(42L, PRESENTE), r(43L, PRESENTE));
        List<RegistroFrequencia> originais = List.copyOf(existente.getRegistros());

        ChamadaResponse resposta = service.atualizar(500L, request(ONTEM, r(41L, FALTA), r(42L, PRESENTE), r(43L, ATRASO)));

        assertThat(existente.getRegistros()).containsExactlyInAnyOrderElementsOf(originais);
        assertThat(resposta.registros()).extracting(ChamadaResponse.Registro::status).containsExactly(FALTA, PRESENTE, ATRASO);
        verify(eventPublisher).publishEvent(any(AttendanceRecordedEvent.class));
    }

    @Test
    void edicaoRemoveMatriculasQueSairamEIncluiAsQueEntraram() {
        Chamada existente = existente(r(41L, PRESENTE), r(42L, PRESENTE), r(43L, PRESENTE));
        ativas.remove(2);
        ativas.add(matricula(44L, 4L));

        service.atualizar(500L, request(ONTEM, r(41L, PRESENTE), r(42L, PRESENTE), r(44L, FALTA)));

        assertThat(existente.getRegistros()).extracting(RegistroFrequencia::getMatriculaId)
                .containsExactlyInAnyOrder(41L, 42L, 44L);
    }

    @Test
    void edicaoFazAsMesmasValidacoes() {
        existente(r(41L, PRESENTE), r(42L, PRESENTE), r(43L, PRESENTE));

        assertThatThrownBy(() -> service.atualizar(500L, request(ONTEM, r(41L, PRESENTE))))
                .isInstanceOf(ValidacaoException.class);
    }

    @Test
    void edicaoParaADataDeOutraChamadaRetorna409() {
        existente(r(41L, PRESENTE), r(42L, PRESENTE), r(43L, PRESENTE));
        LocalDate outraData = ONTEM.minusDays(1);
        when(chamadaRepository.existsByTurmaIdAndDisciplinaIdAndDataAndIdNot(12L, 3L, outraData, 500L)).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(500L, request(outraData, r(41L, PRESENTE), r(42L, PRESENTE), r(43L, PRESENTE))))
                .isInstanceOf(RegraNegocioException.class);
    }

    private Chamada existente(ChamadaRequest.Registro... registros) {
        Chamada chamada = new Chamada();
        chamada.setId(500L);
        chamada.setTurmaId(12L);
        chamada.setDisciplinaId(3L);
        chamada.setData(ONTEM);
        long id = 1;
        for (ChamadaRequest.Registro r : registros) {
            RegistroFrequencia registro = new RegistroFrequencia();
            registro.setId(id++);
            registro.setChamada(chamada);
            registro.setMatriculaId(r.matriculaId());
            registro.setStatus(r.status());
            chamada.getRegistros().add(registro);
        }
        when(chamadaRepository.findById(500L)).thenReturn(Optional.of(chamada));
        return chamada;
    }
}
