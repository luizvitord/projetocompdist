package com.distrischool.gestao_escolar.frequencia;

import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.common.RegraNegocioException;
import com.distrischool.gestao_escolar.common.ValidacaoException;
import com.distrischool.gestao_escolar.event.AttendanceRecordedEvent;
import com.distrischool.gestao_escolar.matricula.Matricula;
import com.distrischool.gestao_escolar.matricula.MatriculaRepository;
import com.distrischool.gestao_escolar.matricula.StatusMatricula;
import com.distrischool.gestao_escolar.turma.TurmaReplica;
import com.distrischool.gestao_escolar.turma.TurmaReplicaService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chamadas. Como nas matrículas, o fallback síncrono de turmas roda antes de abrir a transação.
 */
@Service
public class ChamadaService {

    private final ChamadaRepository chamadaRepository;
    private final MatriculaRepository matriculaRepository;
    private final TurmaReplicaService turmaReplicaService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public ChamadaService(ChamadaRepository chamadaRepository,
                          MatriculaRepository matriculaRepository,
                          TurmaReplicaService turmaReplicaService,
                          ApplicationEventPublisher eventPublisher,
                          PlatformTransactionManager transactionManager) {
        this.chamadaRepository = chamadaRepository;
        this.matriculaRepository = matriculaRepository;
        this.turmaReplicaService = turmaReplicaService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ChamadaResponse registrar(ChamadaRequest request) {
        turmaReplicaService.garantirNaReplica(request.turmaId());
        return transactionTemplate.execute(status -> {
            if (chamadaRepository.existsByTurmaIdAndDisciplinaIdAndData(
                    request.turmaId(), request.disciplinaId(), request.data())) {
                throw duplicada(request);
            }
            Map<Long, Matricula> ativas = validar(request);

            Chamada chamada = new Chamada();
            aplicar(chamada, request);
            chamada = salvar(chamada, request);
            publicar(chamada, ativas);
            return ChamadaResponse.from(chamada);
        });
    }

    /** Substitui os registros da chamada, com as mesmas validações da criação. */
    public ChamadaResponse atualizar(Long id, ChamadaRequest request) {
        turmaReplicaService.garantirNaReplica(request.turmaId());
        return transactionTemplate.execute(status -> {
            Chamada chamada = chamadaRepository.findById(id)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Chamada", id));
            if (chamadaRepository.existsByTurmaIdAndDisciplinaIdAndDataAndIdNot(
                    request.turmaId(), request.disciplinaId(), request.data(), id)) {
                throw duplicada(request);
            }
            Map<Long, Matricula> ativas = validar(request);

            aplicar(chamada, request);
            chamada = salvar(chamada, request);
            publicar(chamada, ativas);
            return ChamadaResponse.from(chamada);
        });
    }

    @Transactional(readOnly = true)
    public Page<ChamadaResponse> buscar(Long turmaId, Long disciplinaId, LocalDate dataInicio, LocalDate dataFim,
                                        Pageable pageable) {
        Specification<Chamada> spec = Specification.unrestricted();
        if (turmaId != null) {
            spec = spec.and(ChamadaSpecifications.turmaIgual(turmaId));
        }
        if (disciplinaId != null) {
            spec = spec.and(ChamadaSpecifications.disciplinaIgual(disciplinaId));
        }
        if (dataInicio != null) {
            spec = spec.and(ChamadaSpecifications.dataAPartirDe(dataInicio));
        }
        if (dataFim != null) {
            spec = spec.and(ChamadaSpecifications.dataAte(dataFim));
        }
        return chamadaRepository.findAll(spec, pageable).map(ChamadaResponse::from);
    }

    @Transactional(readOnly = true)
    public ChamadaResponse buscarPorId(Long id) {
        return ChamadaResponse.from(chamadaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamada", id)));
    }

    /**
     * Turma e oferta válidas, data no ano letivo e exatamente as matrículas ATIVA da turma.
     *
     * @return as matrículas ATIVA da turma, por id
     */
    private Map<Long, Matricula> validar(ChamadaRequest request) {
        TurmaReplica turma = turmaReplicaService.obterComOferta(request.turmaId(), request.disciplinaId());
        if (request.data().isAfter(LocalDate.now())) {
            throw ValidacaoException.campo("data", "não pode ser futura");
        }
        if (request.data().getYear() != turma.getAnoLetivo()) {
            throw ValidacaoException.campo("data", "deve estar no ano letivo da turma (" + turma.getAnoLetivo() + ")");
        }

        Map<Long, Matricula> ativas = matriculaRepository.findByTurmaIdAndStatus(turma.getId(), StatusMatricula.ATIVA)
                .stream().collect(Collectors.toMap(Matricula::getId, Function.identity()));
        Set<Long> informadas = new HashSet<>();
        Set<Long> duplicadas = new TreeSet<>();
        Set<Long> naoEsperadas = new TreeSet<>();
        for (ChamadaRequest.Registro registro : request.registros()) {
            if (!informadas.add(registro.matriculaId())) {
                duplicadas.add(registro.matriculaId());
            } else if (!ativas.containsKey(registro.matriculaId())) {
                naoEsperadas.add(registro.matriculaId());
            }
        }
        Set<Long> faltando = new TreeSet<>(ativas.keySet());
        faltando.removeAll(informadas);

        if (!faltando.isEmpty() || !naoEsperadas.isEmpty() || !duplicadas.isEmpty()) {
            Map<String, Object> divergencias = new LinkedHashMap<>();
            divergencias.put("matriculasFaltando", List.copyOf(faltando));
            divergencias.put("matriculasNaoEsperadas", List.copyOf(naoEsperadas));
            divergencias.put("matriculasDuplicadas", List.copyOf(duplicadas));
            throw new ValidacaoException(
                    "A chamada deve conter exatamente as matrículas ATIVA da turma, cada uma uma vez", divergencias);
        }
        return ativas;
    }

    /**
     * Atualiza os registros no lugar (por matrícula) em vez de apagar e recriar: no flush o
     * Hibernate faz os INSERTs antes dos DELETEs, o que violaria a chave única (chamada, matrícula).
     */
    private static void aplicar(Chamada chamada, ChamadaRequest request) {
        chamada.setTurmaId(request.turmaId());
        chamada.setDisciplinaId(request.disciplinaId());
        chamada.setData(request.data());
        chamada.setProfessorId(request.professorId());

        Map<Long, RegistroFrequencia> existentes = chamada.getRegistros().stream()
                .collect(Collectors.toMap(RegistroFrequencia::getMatriculaId, Function.identity()));
        List<RegistroFrequencia> registros = new ArrayList<>();
        for (ChamadaRequest.Registro r : request.registros()) {
            RegistroFrequencia registro = existentes.get(r.matriculaId());
            if (registro == null) {
                registro = new RegistroFrequencia();
                registro.setChamada(chamada);
                registro.setMatriculaId(r.matriculaId());
            }
            registro.setStatus(r.status());
            registro.setObservacao(r.observacao());
            registros.add(registro);
        }
        // orphanRemoval apaga os registros de matrículas que saíram da chamada
        chamada.getRegistros().retainAll(registros);
        registros.stream().filter(r -> r.getId() == null).forEach(chamada.getRegistros()::add);
    }

    private Chamada salvar(Chamada chamada, ChamadaRequest request) {
        try {
            return chamadaRepository.saveAndFlush(chamada);
        } catch (DataIntegrityViolationException ex) {
            // Chamada da mesma turma, disciplina e data gravada em paralelo
            throw duplicada(request);
        }
    }

    private void publicar(Chamada chamada, Map<Long, Matricula> ativas) {
        List<AttendanceRecordedEvent.Ausencia> ausencias = chamada.getRegistros().stream()
                .filter(r -> r.getStatus() != StatusPresenca.PRESENTE)
                .map(r -> new AttendanceRecordedEvent.Ausencia(ativas.get(r.getMatriculaId()).getAlunoId(), r.getStatus().name()))
                .sorted(Comparator.comparing(AttendanceRecordedEvent.Ausencia::alunoId))
                .toList();
        eventPublisher.publishEvent(new AttendanceRecordedEvent(chamada.getId(), chamada.getTurmaId(),
                chamada.getDisciplinaId(), chamada.getData(), ausencias, Instant.now()));
    }

    private static RegraNegocioException duplicada(ChamadaRequest request) {
        return new RegraNegocioException("Já existe uma chamada da turma " + request.turmaId() + " na disciplina "
                + request.disciplinaId() + " em " + request.data());
    }
}
