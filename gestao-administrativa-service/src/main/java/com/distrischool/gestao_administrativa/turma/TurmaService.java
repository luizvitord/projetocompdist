package com.distrischool.gestao_administrativa.turma;

import com.distrischool.gestao_administrativa.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_administrativa.common.RegraNegocioException;
import com.distrischool.gestao_administrativa.event.ScheduleUpdatedEvent;
import com.distrischool.gestao_administrativa.event.ScheduleUpdatedEvent.Acao;
import com.distrischool.gestao_administrativa.event.TeacherAssignedEvent;
import com.distrischool.gestao_administrativa.professor.Professor;
import com.distrischool.gestao_administrativa.professor.ProfessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TurmaService {

    private final TurmaRepository turmaRepository;
    private final ProfessorService professorService;
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
        turmaRepository.delete(turma);
        publicarScheduleUpdated(turma, Acao.REMOVIDA);
    }

    @Transactional
    public TurmaResponse atribuirProfessor(Long turmaId, Long professorId) {
        Turma turma = obter(turmaId);
        Professor professor = professorService.obter(professorId);
        if (turma.getProfessores().add(professor)) {
            eventPublisher.publishEvent(new TeacherAssignedEvent(
                    professor.getId(), professor.getNome(), professor.getEmail(),
                    turma.getId(), turma.getCodigo(), turma.getNome(), Instant.now()));
        }
        return TurmaResponse.from(turma);
    }

    @Transactional
    public void removerProfessor(Long turmaId, Long professorId) {
        Turma turma = obter(turmaId);
        boolean removido = turma.getProfessores().removeIf(p -> p.getId().equals(professorId));
        if (!removido) {
            throw new RecursoNaoEncontradoException("Professor na turma " + turma.getCodigo(), professorId);
        }
    }

    private Turma obter(Long id) {
        return turmaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma", id));
    }

    private void publicarScheduleUpdated(Turma turma, Acao acao) {
        eventPublisher.publishEvent(new ScheduleUpdatedEvent(turma.getId(), turma.getCodigo(), acao, Instant.now()));
    }

    private static void aplicar(Turma turma, TurmaRequest request) {
        turma.setCodigo(request.codigo());
        turma.setNome(request.nome());
        turma.setAnoLetivo(request.anoLetivo());
        turma.setTurno(request.turno());
        turma.setCapacidade(request.capacidade());
    }
}
