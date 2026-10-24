package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.common.RegraNegocioException;
import com.distrischool.gestao_escolar.turma.TurmaReplicaService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/** Avaliações. Como nas chamadas, o fallback síncrono de turmas roda antes de abrir a transação. */
@Service
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacaoRepository;
    private final NotaRepository notaRepository;
    private final TurmaReplicaService turmaReplicaService;
    private final TransactionTemplate transactionTemplate;

    public AvaliacaoService(AvaliacaoRepository avaliacaoRepository,
                            NotaRepository notaRepository,
                            TurmaReplicaService turmaReplicaService,
                            PlatformTransactionManager transactionManager) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.notaRepository = notaRepository;
        this.turmaReplicaService = turmaReplicaService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AvaliacaoResponse criar(AvaliacaoRequest request) {
        turmaReplicaService.garantirNaReplica(request.turmaId());
        return transactionTemplate.execute(status -> {
            turmaReplicaService.obterComOferta(request.turmaId(), request.disciplinaId());
            if (request.tipo() == TipoAvaliacao.RECUPERACAO && avaliacaoRepository.existsByTurmaIdAndDisciplinaIdAndBimestreAndTipo(
                    request.turmaId(), request.disciplinaId(), request.bimestre(), TipoAvaliacao.RECUPERACAO)) {
                throw recuperacaoDuplicada(request);
            }
            Avaliacao avaliacao = new Avaliacao();
            aplicar(avaliacao, request);
            return AvaliacaoResponse.from(salvar(avaliacao, request));
        });
    }

    @Transactional(readOnly = true)
    public Page<AvaliacaoResponse> buscar(Long turmaId, Long disciplinaId, Integer bimestre, Pageable pageable) {
        Specification<Avaliacao> spec = Specification.unrestricted();
        if (turmaId != null) {
            spec = spec.and(AvaliacaoSpecifications.turmaIgual(turmaId));
        }
        if (disciplinaId != null) {
            spec = spec.and(AvaliacaoSpecifications.disciplinaIgual(disciplinaId));
        }
        if (bimestre != null) {
            spec = spec.and(AvaliacaoSpecifications.bimestreIgual(bimestre));
        }
        return avaliacaoRepository.findAll(spec, pageable).map(AvaliacaoResponse::from);
    }

    @Transactional(readOnly = true)
    public AvaliacaoResponse buscarPorId(Long id) {
        return AvaliacaoResponse.from(obter(id));
    }

    /**
     * Com notas lançadas, turma, disciplina e nota máxima não podem mudar: as notas pertencem a
     * matrículas da turma e foram dadas naquela escala.
     */
    public AvaliacaoResponse atualizar(Long id, AvaliacaoRequest request) {
        turmaReplicaService.garantirNaReplica(request.turmaId());
        return transactionTemplate.execute(status -> {
            Avaliacao avaliacao = obter(id);
            turmaReplicaService.obterComOferta(request.turmaId(), request.disciplinaId());
            boolean mudaBase = !Objects.equals(avaliacao.getTurmaId(), request.turmaId())
                    || !Objects.equals(avaliacao.getDisciplinaId(), request.disciplinaId())
                    || avaliacao.getNotaMaxima().compareTo(request.notaMaximaOuPadrao()) != 0;
            if (mudaBase && notaRepository.existsByAvaliacaoId(id)) {
                throw new RegraNegocioException(
                        "A avaliação já tem notas: turma, disciplina e nota máxima não podem ser alteradas");
            }
            if (request.tipo() == TipoAvaliacao.RECUPERACAO && avaliacaoRepository.existsByTurmaIdAndDisciplinaIdAndBimestreAndTipoAndIdNot(
                    request.turmaId(), request.disciplinaId(), request.bimestre(), TipoAvaliacao.RECUPERACAO, id)) {
                throw recuperacaoDuplicada(request);
            }
            aplicar(avaliacao, request);
            return AvaliacaoResponse.from(salvar(avaliacao, request));
        });
    }

    @Transactional
    public void excluir(Long id) {
        Avaliacao avaliacao = obter(id);
        if (notaRepository.existsByAvaliacaoId(id)) {
            throw new RegraNegocioException("A avaliação já tem notas e não pode ser excluída");
        }
        avaliacaoRepository.delete(avaliacao);
    }

    public Avaliacao obter(Long id) {
        return avaliacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Avaliação", id));
    }

    /** O índice único parcial de recuperação barra duas criadas em paralelo: 409, não 500. */
    private Avaliacao salvar(Avaliacao avaliacao, AvaliacaoRequest request) {
        try {
            return avaliacaoRepository.saveAndFlush(avaliacao);
        } catch (DataIntegrityViolationException ex) {
            throw recuperacaoDuplicada(request);
        }
    }

    private static RegraNegocioException recuperacaoDuplicada(AvaliacaoRequest request) {
        return new RegraNegocioException("Já existe uma recuperação da disciplina " + request.disciplinaId()
                + " na turma " + request.turmaId() + " no " + request.bimestre() + "º bimestre");
    }

    private static void aplicar(Avaliacao avaliacao, AvaliacaoRequest request) {
        avaliacao.setTurmaId(request.turmaId());
        avaliacao.setDisciplinaId(request.disciplinaId());
        avaliacao.setBimestre(request.bimestre());
        avaliacao.setTipo(request.tipo());
        avaliacao.setDescricao(request.descricao());
        avaliacao.setPeso(request.pesoOuPadrao());
        avaliacao.setNotaMaxima(request.notaMaximaOuPadrao());
        avaliacao.setData(request.data());
    }
}
