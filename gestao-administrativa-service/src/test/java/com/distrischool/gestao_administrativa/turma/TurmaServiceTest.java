package com.distrischool.gestao_administrativa.turma;

import com.distrischool.gestao_administrativa.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_administrativa.common.RegraNegocioException;
import com.distrischool.gestao_administrativa.disciplina.Disciplina;
import com.distrischool.gestao_administrativa.disciplina.DisciplinaAlteradaEvent;
import com.distrischool.gestao_administrativa.disciplina.DisciplinaService;
import com.distrischool.gestao_administrativa.event.ScheduleUpdatedEvent;
import com.distrischool.gestao_administrativa.event.ScheduleUpdatedEvent.Acao;
import com.distrischool.gestao_administrativa.event.TeacherAssignedEvent;
import com.distrischool.gestao_administrativa.professor.Professor;
import com.distrischool.gestao_administrativa.professor.ProfessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras de oferta de disciplinas; sem Spring, com repositórios e serviços simulados. */
class TurmaServiceTest {

    private TurmaRepository turmaRepository;
    private ProfessorService professorService;
    private DisciplinaService disciplinaService;
    private ApplicationEventPublisher eventPublisher;
    private TurmaService turmaService;

    private Turma turma;
    private Disciplina matematica;
    private Professor carlos;

    @BeforeEach
    void preparar() {
        turmaRepository = mock(TurmaRepository.class);
        professorService = mock(ProfessorService.class);
        disciplinaService = mock(DisciplinaService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        turmaService = new TurmaService(turmaRepository, professorService, disciplinaService, eventPublisher);

        turma = new Turma();
        turma.setId(12L);
        turma.setCodigo("9A-2026");
        turma.setNome("9º Ano A");
        turma.setAnoLetivo(2026);
        turma.setTurno(Turno.MANHA);
        turma.setCapacidade(35);
        matematica = disciplina(3L, "MAT", "Matemática", true);
        carlos = professor(7L, "Carlos Lima");

        when(turmaRepository.findById(12L)).thenReturn(Optional.of(turma));
        when(disciplinaService.obter(3L)).thenReturn(matematica);
        when(professorService.obter(7L)).thenReturn(carlos);
    }

    @Test
    void ofertaDisciplinaSemProfessor() {
        TurmaResponse resposta = turmaService.ofertarDisciplina(12L, 3L, null);

        assertThat(resposta.disciplinas()).singleElement().satisfies(d -> {
            assertThat(d.disciplinaId()).isEqualTo(3L);
            assertThat(d.codigo()).isEqualTo("MAT");
            assertThat(d.professorId()).isNull();
            assertThat(d.professorNome()).isNull();
        });
        verify(eventPublisher, never()).publishEvent(any(TeacherAssignedEvent.class));
    }

    @Test
    void ofertaComProfessorNaoAtribuidoAtribuiEPublicaTeacherAssigned() {
        TurmaResponse resposta = turmaService.ofertarDisciplina(12L, 3L, 7L);

        assertThat(resposta.professores()).extracting(TurmaResponse.ProfessorResumo::id).containsExactly(7L);
        assertThat(resposta.disciplinas()).singleElement()
                .extracting(TurmaResponse.DisciplinaOfertada::professorId).isEqualTo(7L);
        verify(eventPublisher).publishEvent(any(TeacherAssignedEvent.class));
    }

    @Test
    void ofertaComProfessorJaAtribuidoNaoRepublicaTeacherAssigned() {
        turma.getProfessores().add(carlos);

        turmaService.ofertarDisciplina(12L, 3L, 7L);

        verify(eventPublisher, never()).publishEvent(any(TeacherAssignedEvent.class));
    }

    @Test
    void trocaProfessorDaOfertaExistenteSemDuplicarAOferta() {
        turmaService.ofertarDisciplina(12L, 3L, null);
        Professor marina = professor(8L, "Marina Reis");
        when(professorService.obter(8L)).thenReturn(marina);

        TurmaResponse resposta = turmaService.ofertarDisciplina(12L, 3L, 8L);

        assertThat(turma.getDisciplinas()).hasSize(1);
        assertThat(resposta.disciplinas()).singleElement()
                .extracting(TurmaResponse.DisciplinaOfertada::professorNome).isEqualTo("Marina Reis");
    }

    @Test
    void disciplinaInativaNaoPodeSerOfertada() {
        matematica.setAtiva(false);

        assertThatThrownBy(() -> turmaService.ofertarDisciplina(12L, 3L, 7L))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(turma.getDisciplinas()).isEmpty();
        assertThat(turma.getProfessores()).isEmpty();
        verify(eventPublisher, never()).publishEvent(any(TeacherAssignedEvent.class));
    }

    @Test
    void professorInexistenteRetorna404SemOfertar() {
        when(professorService.obter(99L)).thenThrow(new RecursoNaoEncontradoException("Professor", 99L));

        assertThatThrownBy(() -> turmaService.ofertarDisciplina(12L, 3L, 99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        assertThat(turma.getDisciplinas()).isEmpty();
    }

    @Test
    void retiraOferta() {
        turmaService.ofertarDisciplina(12L, 3L, null);

        turmaService.retirarDisciplina(12L, 3L);

        assertThat(turma.getDisciplinas()).isEmpty();
    }

    @Test
    void retirarDisciplinaNaoOfertadaRetorna404() {
        assertThatThrownBy(() -> turmaService.retirarDisciplina(12L, 3L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // --- schedule.updated ---

    @Test
    void criarPublicaScheduleUpdatedCriadaComOEstadoDaTurma() {
        when(turmaRepository.save(any())).thenAnswer(inv -> {
            Turma t = inv.getArgument(0);
            t.setId(12L);
            return t;
        });

        turmaService.criar(new TurmaRequest("9A-2026", "9º Ano A", 2026, Turno.MANHA, 35));

        ScheduleUpdatedEvent evento = unicoScheduleUpdated();
        assertThat(evento.acao()).isEqualTo(Acao.CRIADA);
        assertThat(evento.turmaId()).isEqualTo(12L);
        assertThat(evento.codigo()).isEqualTo("9A-2026");
        assertThat(evento.nome()).isEqualTo("9º Ano A");
        assertThat(evento.anoLetivo()).isEqualTo(2026);
        assertThat(evento.turno()).isEqualTo("MANHA");
        assertThat(evento.capacidade()).isEqualTo(35);
        assertThat(evento.disciplinas()).isEmpty();
        assertThat(evento.occurredAt()).isNotNull();
    }

    @Test
    void atualizarPublicaScheduleUpdatedAtualizadaComAsDisciplinas() {
        turmaService.ofertarDisciplina(12L, 3L, 7L);
        limparEventos();

        turmaService.atualizar(12L, new TurmaRequest("9A-2026", "9º Ano A (novo)", 2026, Turno.TARDE, null));

        ScheduleUpdatedEvent evento = unicoScheduleUpdated();
        assertThat(evento.acao()).isEqualTo(Acao.ATUALIZADA);
        assertThat(evento.nome()).isEqualTo("9º Ano A (novo)");
        assertThat(evento.turno()).isEqualTo("TARDE");
        assertThat(evento.capacidade()).isNull();
        assertThat(evento.disciplinas()).containsExactly(
                new ScheduleUpdatedEvent.DisciplinaOfertada(3L, "MAT", "Matemática", 7L));
    }

    @Test
    void excluirPublicaScheduleUpdatedRemovidaComOUltimoEstado() {
        turmaService.ofertarDisciplina(12L, 3L, 7L);
        limparEventos();

        turmaService.excluir(12L);

        ScheduleUpdatedEvent evento = unicoScheduleUpdated();
        assertThat(evento.acao()).isEqualTo(Acao.REMOVIDA);
        assertThat(evento.turmaId()).isEqualTo(12L);
        assertThat(evento.codigo()).isEqualTo("9A-2026");
        assertThat(evento.disciplinas()).extracting(ScheduleUpdatedEvent.DisciplinaOfertada::disciplinaId)
                .containsExactly(3L);
        verify(turmaRepository).delete(turma);
    }

    @Test
    void ofertarPublicaScheduleUpdatedComAListaCompletaDeDisciplinas() {
        turmaService.ofertarDisciplina(12L, 3L, null);
        Disciplina portugues = disciplina(4L, "POR", "Português", true);
        when(disciplinaService.obter(4L)).thenReturn(portugues);
        limparEventos();

        turmaService.ofertarDisciplina(12L, 4L, 7L);

        ScheduleUpdatedEvent evento = unicoScheduleUpdated();
        assertThat(evento.acao()).isEqualTo(Acao.ATUALIZADA);
        assertThat(evento.disciplinas()).containsExactly(
                new ScheduleUpdatedEvent.DisciplinaOfertada(3L, "MAT", "Matemática", null),
                new ScheduleUpdatedEvent.DisciplinaOfertada(4L, "POR", "Português", 7L));
    }

    @Test
    void trocarOProfessorDaOfertaPublicaScheduleUpdated() {
        turmaService.ofertarDisciplina(12L, 3L, 7L);
        when(professorService.obter(8L)).thenReturn(professor(8L, "Marina Reis"));
        limparEventos();

        turmaService.ofertarDisciplina(12L, 3L, 8L);

        assertThat(unicoScheduleUpdated().disciplinas()).singleElement()
                .extracting(ScheduleUpdatedEvent.DisciplinaOfertada::professorId).isEqualTo(8L);
    }

    @Test
    void repetirAMesmaOfertaNaoPublicaScheduleUpdated() {
        turmaService.ofertarDisciplina(12L, 3L, 7L);
        limparEventos();

        turmaService.ofertarDisciplina(12L, 3L, 7L);

        assertThat(scheduleUpdatedPublicados()).isEmpty();
    }

    @Test
    void retirarDisciplinaPublicaScheduleUpdatedSemEla() {
        turmaService.ofertarDisciplina(12L, 3L, null);
        limparEventos();

        turmaService.retirarDisciplina(12L, 3L);

        ScheduleUpdatedEvent evento = unicoScheduleUpdated();
        assertThat(evento.acao()).isEqualTo(Acao.ATUALIZADA);
        assertThat(evento.disciplinas()).isEmpty();
    }

    @Test
    void falhasDeOfertaNaoPublicamScheduleUpdated() {
        matematica.setAtiva(false);

        assertThatThrownBy(() -> turmaService.ofertarDisciplina(12L, 3L, null))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> turmaService.retirarDisciplina(12L, 3L))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        assertThat(scheduleUpdatedPublicados()).isEmpty();
    }

    @Test
    void disciplinaAlteradaPublicaUmScheduleUpdatedParaCadaTurmaQueAOferta() {
        Turma outra = new Turma();
        outra.setId(13L);
        outra.setCodigo("9B-2026");
        outra.setNome("9º Ano B");
        outra.setTurno(Turno.TARDE);
        TurmaDisciplina oferta = new TurmaDisciplina();
        oferta.setTurma(outra);
        oferta.setDisciplina(matematica);
        outra.getDisciplinas().add(oferta);
        turmaService.ofertarDisciplina(12L, 3L, null);
        limparEventos();
        matematica.setNome("Matemática Básica");
        matematica.setCodigo("MTB");
        when(turmaRepository.findByDisciplinaId(3L)).thenReturn(List.of(turma, outra));

        turmaService.aoAlterarDisciplina(new DisciplinaAlteradaEvent(3L));

        List<ScheduleUpdatedEvent> eventos = scheduleUpdatedPublicados();
        assertThat(eventos).extracting(ScheduleUpdatedEvent::turmaId).containsExactly(12L, 13L);
        assertThat(eventos).allSatisfy(e -> {
            assertThat(e.acao()).isEqualTo(Acao.ATUALIZADA);
            assertThat(e.disciplinas()).singleElement().satisfies(d -> {
                assertThat(d.codigo()).isEqualTo("MTB");
                assertThat(d.nome()).isEqualTo("Matemática Básica");
            });
        });
    }

    @Test
    void disciplinaAlteradaSemTurmasNaoPublicaNada() {
        when(turmaRepository.findByDisciplinaId(3L)).thenReturn(List.of());

        turmaService.aoAlterarDisciplina(new DisciplinaAlteradaEvent(3L));

        assertThat(scheduleUpdatedPublicados()).isEmpty();
    }

    private List<ScheduleUpdatedEvent> scheduleUpdatedPublicados() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(ScheduleUpdatedEvent.class::isInstance)
                .map(ScheduleUpdatedEvent.class::cast)
                .toList();
    }

    private ScheduleUpdatedEvent unicoScheduleUpdated() {
        assertThat(scheduleUpdatedPublicados()).hasSize(1);
        return scheduleUpdatedPublicados().get(0);
    }

    private void limparEventos() {
        clearInvocations(eventPublisher);
    }

    private static Disciplina disciplina(Long id, String codigo, String nome, boolean ativa) {
        Disciplina d = new Disciplina();
        d.setId(id);
        d.setCodigo(codigo);
        d.setNome(nome);
        d.setAtiva(ativa);
        return d;
    }

    private static Professor professor(Long id, String nome) {
        Professor p = new Professor();
        p.setId(id);
        p.setNome(nome);
        p.setEmail(nome.toLowerCase().replace(' ', '.') + "@example.com");
        return p;
    }
}
