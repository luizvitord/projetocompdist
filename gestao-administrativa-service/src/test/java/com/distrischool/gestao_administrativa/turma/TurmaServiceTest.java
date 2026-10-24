package com.distrischool.gestao_administrativa.turma;

import com.distrischool.gestao_administrativa.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_administrativa.common.RegraNegocioException;
import com.distrischool.gestao_administrativa.disciplina.Disciplina;
import com.distrischool.gestao_administrativa.disciplina.DisciplinaService;
import com.distrischool.gestao_administrativa.event.TeacherAssignedEvent;
import com.distrischool.gestao_administrativa.professor.Professor;
import com.distrischool.gestao_administrativa.professor.ProfessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
