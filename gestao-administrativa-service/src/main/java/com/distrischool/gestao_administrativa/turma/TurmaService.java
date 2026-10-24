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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TurmaService {

    private final TurmaRepository turmaRepository;
    private final ProfessorService professorService;
    private final DisciplinaService disciplinaService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TurmaResponse criar(TurmaRequest request) {
        if (turmaRepository.existsByCodigoIgnoreCase(request.codigo())) {
            throw new RegraNegocioException("Já existe uma turma com o código " + request.codigo());
        }
        Turma turma = new Turma();
        aplicar(turma, request);
        turma = turmaRepository.save(turma);

        publicarScheduleUpdated(turma, Acao.CRIADA);
        return TurmaResponse.from(turma);
    }

    @Transactional(readOnly = true)
    public Page<TurmaResponse> buscar(Integer anoLetivo, Turno turno, Long professorId, Pageable pageable) {
        Specification<Turma> spec = Specification.unrestricted();
        if (anoLetivo != null) {
            spec = spec.and(TurmaSpecifications.anoLetivoIgual(anoLetivo));
        }
        if (turno != null) {
            spec = spec.and(TurmaSpecifications.turnoIgual(turno));
        }
        if (professorId != null) {
            spec = spec.and(TurmaSpecifications.possuiProfessor(professorId));
        }
        return turmaRepository.findAll(spec, pageable).map(TurmaResponse::from);
    }

    @Transactional(readOnly = true)
    public TurmaResponse buscarPorId(Long id) {
        return TurmaResponse.from(obter(id));
    }

    @Transactional
    public TurmaResponse atualizar(Long id, TurmaRequest request) {
        Turma turma = obter(id);
        if (turmaRepository.existsByCodigoIgnoreCaseAndIdNot(request.codigo(), id)) {
            throw new RegraNegocioException("Já existe uma turma com o código " + request.codigo());
        }
        aplicar(turma, request);

        publicarScheduleUpdated(turma, Acao.ATUALIZADA);
        return TurmaResponse.from(turma);
    }

    @Transactional
    public void excluir(Long id) {
        Turma turma = obter(id);
        // O evento carrega o último estado da turma, então é montado antes da remoção
        ScheduleUpdatedEvent evento = montarScheduleUpdated(turma, Acao.REMOVIDA);
        turmaRepository.delete(turma);
        eventPublisher.publishEvent(evento);
    }

    @Transactional
    public TurmaResponse atribuirProfessor(Long turmaId, Long professorId) {
        Turma turma = obter(turmaId);
        atribuirSeNecessario(turma, professorService.obter(professorId));
        return TurmaResponse.from(turma);
    }

    /**
     * Oferta a disciplina na turma ou, se ela já for ofertada, troca o professor responsável.
     * Um professor ainda não atribuído à turma é atribuído automaticamente.
     */
    @Transactional
    public TurmaResponse ofertarDisciplina(Long turmaId, Long disciplinaId, Long professorId) {
        Turma turma = obter(turmaId);
        Disciplina disciplina = disciplinaService.obter(disciplinaId);
        Professor professor = professorId != null ? professorService.obter(professorId) : null;
        if (!disciplina.isAtiva()) {
            throw new RegraNegocioException("A disciplina " + disciplina.getCodigo() + " está inativa e não pode ser ofertada");
        }

        TurmaDisciplina existente = turma.getDisciplinas().stream()
                .filter(td -> td.getDisciplina().getId().equals(disciplinaId))
                .findFirst()
                .orElse(null);
        boolean mudou = existente == null || !Objects.equals(idDoProfessor(existente), professorId);

        TurmaDisciplina oferta = existente;
        if (oferta == null) {
            oferta = new TurmaDisciplina();
            oferta.setTurma(turma);
            oferta.setDisciplina(disciplina);
            turma.getDisciplinas().add(oferta);
        }
        oferta.setProfessor(professor);
        if (professor != null) {
            atribuirSeNecessario(turma, professor);
        }
        flushComoRegraNegocio();

        // Repetir a mesma oferta (mesma disciplina, mesmo professor) não altera nada
        if (mudou) {
            publicarScheduleUpdated(turma, Acao.ATUALIZADA);
        }
        return TurmaResponse.from(turma);
    }

    @Transactional
    public void retirarDisciplina(Long turmaId, Long disciplinaId) {
        Turma turma = obter(turmaId);
        boolean removida = turma.getDisciplinas().removeIf(td -> td.getDisciplina().getId().equals(disciplinaId));
        if (!removida) {
            throw new RecursoNaoEncontradoException("Disciplina na turma " + turma.getCodigo(), disciplinaId);
        }
        publicarScheduleUpdated(turma, Acao.ATUALIZADA);
    }

    /**
     * Uma disciplina renomeada (ou com o código alterado) muda o estado de cada turma que a oferta:
     * publica um {@code schedule.updated} para cada uma delas. Roda na transação da alteração,
     * então o Kafka só recebe os eventos depois do commit.
     */
    @EventListener
    void aoAlterarDisciplina(DisciplinaAlteradaEvent event) {
        turmaRepository.findByDisciplinaId(event.disciplinaId())
                .forEach(turma -> publicarScheduleUpdated(turma, Acao.ATUALIZADA));
    }

    @Transactional
    public void removerProfessor(Long turmaId, Long professorId) {
        Turma turma = obter(turmaId);
        boolean removido = turma.getProfessores().removeIf(p -> p.getId().equals(professorId));
        if (!removido) {
            throw new RecursoNaoEncontradoException("Professor na turma " + turma.getCodigo(), professorId);
        }
    }

    private void atribuirSeNecessario(Turma turma, Professor professor) {
        if (turma.getProfessores().add(professor)) {
            eventPublisher.publishEvent(new TeacherAssignedEvent(
                    professor.getId(), professor.getNome(), professor.getEmail(),
                    turma.getId(), turma.getCodigo(), turma.getNome(), Instant.now()));
        }
    }

    /** Uma oferta duplicada criada em concorrência viola a chave única e deve virar 409, não 500. */
    private void flushComoRegraNegocio() {
        try {
            turmaRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new RegraNegocioException("A disciplina já está ofertada nesta turma");
        }
    }

    private Turma obter(Long id) {
        return turmaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma", id));
    }

    private void publicarScheduleUpdated(Turma turma, Acao acao) {
        eventPublisher.publishEvent(montarScheduleUpdated(turma, acao));
    }

    private static ScheduleUpdatedEvent montarScheduleUpdated(Turma turma, Acao acao) {
        List<ScheduleUpdatedEvent.DisciplinaOfertada> disciplinas = turma.getDisciplinas().stream()
                .map(td -> new ScheduleUpdatedEvent.DisciplinaOfertada(
                        td.getDisciplina().getId(),
                        td.getDisciplina().getCodigo(),
                        td.getDisciplina().getNome(),
                        idDoProfessor(td)))
                .sorted(Comparator.comparing(ScheduleUpdatedEvent.DisciplinaOfertada::codigo))
                .toList();
        return new ScheduleUpdatedEvent(
                turma.getId(), turma.getCodigo(), turma.getNome(), turma.getAnoLetivo(),
                turma.getTurno().name(), turma.getCapacidade(), disciplinas, acao, Instant.now());
    }

    private static Long idDoProfessor(TurmaDisciplina oferta) {
        return oferta.getProfessor() != null ? oferta.getProfessor().getId() : null;
    }

    private static void aplicar(Turma turma, TurmaRequest request) {
        turma.setCodigo(request.codigo());
        turma.setNome(request.nome());
        turma.setAnoLetivo(request.anoLetivo());
        turma.setTurno(request.turno());
        turma.setCapacidade(request.capacidade());
    }
}
