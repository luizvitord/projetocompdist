package com.distrischool.gestao_escolar.matricula;

import com.distrischool.gestao_escolar.aluno.AlunoService;
import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.common.RegraNegocioException;
import com.distrischool.gestao_escolar.common.ValidacaoException;
import com.distrischool.gestao_escolar.event.EnrollmentCreatedEvent;
import com.distrischool.gestao_escolar.event.EnrollmentUpdatedEvent;
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
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Regras de matrícula. As operações que podem precisar do fallback síncrono de turmas chamam
 * {@link TurmaReplicaService#garantirNaReplica} antes de abrir a transação, para não segurar
 * uma conexão do banco enquanto esperam o outro serviço.
 */
@Service
public class MatriculaService {

    private static final String JA_VIGENTE = "O aluno já tem uma matrícula ativa ou trancada no ano letivo ";

    private final MatriculaRepository matriculaRepository;
    private final AlunoService alunoService;
    private final TurmaReplicaService turmaReplicaService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public MatriculaService(MatriculaRepository matriculaRepository,
                            AlunoService alunoService,
                            TurmaReplicaService turmaReplicaService,
                            ApplicationEventPublisher eventPublisher,
                            PlatformTransactionManager transactionManager) {
        this.matriculaRepository = matriculaRepository;
        this.alunoService = alunoService;
        this.turmaReplicaService = turmaReplicaService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public MatriculaResponse criar(MatriculaRequest request) {
        alunoService.obter(request.alunoId());
        turmaReplicaService.garantirNaReplica(request.turmaId());
        return transactionTemplate.execute(status -> {
            // O bloqueio na turma serializa matrículas simultâneas disputando a última vaga
            TurmaReplica turma = turmaReplicaService.obterComBloqueio(request.turmaId());
            verificarTurmaDisponivel(turma);
            if (matriculaRepository.existsByAlunoIdAndAnoLetivoAndStatusIn(
                    request.alunoId(), turma.getAnoLetivo(), StatusMatricula.VIGENTES)) {
                throw new RegraNegocioException(JA_VIGENTE + turma.getAnoLetivo());
            }
            verificarVaga(turma);

            Matricula matricula = salvar(nova(request.alunoId(), turma, null), turma.getAnoLetivo());
            publicarCriada(matricula);
            return MatriculaResponse.from(matricula);
        });
    }

    @Transactional(readOnly = true)
    public Page<MatriculaResponse> buscar(Long alunoId, Long turmaId, Integer anoLetivo, StatusMatricula status,
                                          Pageable pageable) {
        Specification<Matricula> spec = Specification.unrestricted();
        if (alunoId != null) {
            spec = spec.and(MatriculaSpecifications.alunoIgual(alunoId));
        }
        if (turmaId != null) {
            spec = spec.and(MatriculaSpecifications.turmaIgual(turmaId));
        }
        if (anoLetivo != null) {
            spec = spec.and(MatriculaSpecifications.anoLetivoIgual(anoLetivo));
        }
        if (status != null) {
            spec = spec.and(MatriculaSpecifications.statusIgual(status));
        }
        return matriculaRepository.findAll(spec, pageable).map(MatriculaResponse::from);
    }

    @Transactional(readOnly = true)
    public MatriculaResponse buscarPorId(Long id) {
        return MatriculaResponse.from(obter(id));
    }

    /**
     * Muda o status conforme as transições de {@link StatusMatricula}. {@code TRANSFERIDA} por aqui
     * é a saída do aluno para outra escola (sem turma de destino); a transferência entre turmas é
     * feita em {@link #transferir}.
     */
    public MatriculaResponse alterarStatus(Long id, AlteracaoStatusRequest request) {
        StatusMatricula novo = request.status();
        String motivo = StringUtils.hasText(request.motivo()) ? request.motivo().trim() : null;
        if (novo.exigeMotivo() && motivo == null) {
            throw ValidacaoException.campo("motivo", "é obrigatório para o status " + novo);
        }
        return transactionTemplate.execute(status -> {
            Matricula matricula = obterComBloqueio(id);
            StatusMatricula anterior = matricula.getStatus();
            verificarTransicao(anterior, novo);
            if (novo == StatusMatricula.ATIVA) {
                // Reativação de uma matrícula trancada volta a ocupar vaga
                TurmaReplica turma = turmaReplicaService.obterComBloqueio(matricula.getTurmaId());
                verificarTurmaDisponivel(turma);
                verificarVaga(turma);
            }

            matricula.setStatus(novo);
            if (motivo != null) {
                matricula.setMotivo(motivo);
            }
            matricula.setDataEncerramento(novo.isFinal() ? LocalDate.now() : null);
            matriculaRepository.save(matricula);

            eventPublisher.publishEvent(new EnrollmentUpdatedEvent(matricula.getId(), matricula.getAlunoId(),
                    matricula.getTurmaId(), anterior.name(), novo.name(), null, Instant.now()));
            return MatriculaResponse.from(matricula);
        });
    }

    /**
     * Encerra a matrícula atual como {@code TRANSFERIDA} e cria uma nova {@code ATIVA} na turma de
     * destino (mesmo ano letivo), na mesma transação.
     *
     * @return a nova matrícula
     */
    public MatriculaResponse transferir(Long id, TransferenciaRequest request) {
        turmaReplicaService.garantirNaReplica(request.turmaDestinoId());
        return transactionTemplate.execute(status -> {
            Matricula origem = obterComBloqueio(id);
            verificarTransicao(origem.getStatus(), StatusMatricula.TRANSFERIDA);
            if (origem.getTurmaId().equals(request.turmaDestinoId())) {
                throw new RegraNegocioException("A turma de destino é a turma atual da matrícula");
            }
            TurmaReplica destino = turmaReplicaService.obterComBloqueio(request.turmaDestinoId());
            verificarTurmaDisponivel(destino);
            if (!destino.getAnoLetivo().equals(origem.getAnoLetivo())) {
                throw new RegraNegocioException("A turma de destino é do ano letivo " + destino.getAnoLetivo()
                        + ", e a matrícula é de " + origem.getAnoLetivo());
            }
            verificarVaga(destino);

            origem.setStatus(StatusMatricula.TRANSFERIDA);
            origem.setMotivo(request.motivo().trim());
            origem.setDataEncerramento(LocalDate.now());
            // O Hibernate faz os INSERTs antes dos UPDATEs no flush: sem este flush, a nova matrícula
            // ATIVA violaria o índice único enquanto a origem ainda estivesse ATIVA no banco
            matriculaRepository.saveAndFlush(origem);
            Matricula nova = salvar(nova(origem.getAlunoId(), destino, origem.getId()), destino.getAnoLetivo());

            eventPublisher.publishEvent(new EnrollmentUpdatedEvent(origem.getId(), origem.getAlunoId(),
                    origem.getTurmaId(), StatusMatricula.ATIVA.name(), StatusMatricula.TRANSFERIDA.name(),
                    destino.getId(), Instant.now()));
            publicarCriada(nova);
            return MatriculaResponse.from(nova);
        });
    }

    public Matricula obter(Long id) {
        return matriculaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Matrícula", id));
    }

    private Matricula obterComBloqueio(Long id) {
        return matriculaRepository.findByIdParaAtualizacao(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Matrícula", id));
    }

    private static void verificarTransicao(StatusMatricula anterior, StatusMatricula novo) {
        if (!anterior.podeIrPara(novo)) {
            throw new RegraNegocioException("Transição de status inválida: " + anterior + " → " + novo);
        }
    }

    private static void verificarTurmaDisponivel(TurmaReplica turma) {
        if (turma.isRemovida()) {
            throw new RegraNegocioException("A turma " + turma.getCodigo() + " foi removida");
        }
    }

    /** Chame com a turma bloqueada, para a contagem não mudar até o commit. */
    private void verificarVaga(TurmaReplica turma) {
        if (turma.getCapacidade() == null) {
            return;
        }
        long ativas = matriculaRepository.countByTurmaIdAndStatus(turma.getId(), StatusMatricula.ATIVA);
        if (ativas >= turma.getCapacidade()) {
            throw new RegraNegocioException("A turma " + turma.getCodigo() + " não tem vagas (capacidade "
                    + turma.getCapacidade() + ")");
        }
    }

    /**
     * O índice único parcial (aluno, ano letivo) é a última barreira contra duas matrículas
     * vigentes criadas em paralelo para o mesmo aluno: a violação vira 409, não 500.
     */
    private Matricula salvar(Matricula matricula, Integer anoLetivo) {
        try {
            return matriculaRepository.saveAndFlush(matricula);
        } catch (DataIntegrityViolationException ex) {
            throw new RegraNegocioException(JA_VIGENTE + anoLetivo);
        }
    }

    private static Matricula nova(Long alunoId, TurmaReplica turma, Long matriculaOrigemId) {
        Matricula matricula = new Matricula();
        matricula.setAlunoId(alunoId);
        matricula.setTurmaId(turma.getId());
        matricula.setAnoLetivo(turma.getAnoLetivo());
        matricula.setStatus(StatusMatricula.ATIVA);
        matricula.setDataMatricula(LocalDate.now());
        matricula.setMatriculaOrigemId(matriculaOrigemId);
        return matricula;
    }

    private void publicarCriada(Matricula matricula) {
        eventPublisher.publishEvent(new EnrollmentCreatedEvent(matricula.getId(), matricula.getAlunoId(),
                matricula.getTurmaId(), matricula.getAnoLetivo(), Instant.now()));
    }
}
