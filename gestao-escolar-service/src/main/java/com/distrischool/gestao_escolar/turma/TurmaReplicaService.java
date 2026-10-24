package com.distrischool.gestao_escolar.turma;

import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.common.RegraNegocioException;
import com.distrischool.gestao_escolar.turma.ScheduleUpdatedMessage.Acao;
import com.distrischool.gestao_escolar.turma.ScheduleUpdatedMessage.DisciplinaOfertada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mantém a réplica local de turmas e disciplinas ofertadas. É a única classe que escreve em
 * {@code turma_replica} e {@code turma_disciplina_replica}.
 */
@Slf4j
@Service
public class TurmaReplicaService {

    private final TurmaReplicaRepository turmaReplicaRepository;
    private final AdministrativaClient administrativaClient;
    private final TransactionTemplate transactionTemplate;

    public TurmaReplicaService(TurmaReplicaRepository turmaReplicaRepository,
                               AdministrativaClient administrativaClient,
                               PlatformTransactionManager transactionManager) {
        this.turmaReplicaRepository = turmaReplicaRepository;
        this.administrativaClient = administrativaClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Aplica um {@code schedule.updated}. Idempotente: faz upsert pela chave e ignora eventos com
     * {@code occurredAt} anterior ao último aplicado (reentrega ou entrega fora de ordem).
     *
     * @return {@code false} se o evento foi ignorado por ser antigo
     */
    @Transactional
    public boolean aplicar(ScheduleUpdatedMessage mensagem) {
        validar(mensagem);
        // O TIMESTAMPTZ guarda microssegundos e arredonda o resto: sem truncar aqui, a reentrega
        // do último evento pareceria mais antiga que o valor gravado
        Instant occurredAt = mensagem.occurredAt().truncatedTo(ChronoUnit.MICROS);
        TurmaReplica turma = turmaReplicaRepository.findByIdParaAtualizacao(mensagem.turmaId()).orElse(null);
        if (turma != null && turma.getUltimoEventoEm() != null && occurredAt.isBefore(turma.getUltimoEventoEm())) {
            log.info("schedule.updated ignorado: turma {} já reflete o evento de {} (recebido evento de {})",
                    mensagem.turmaId(), turma.getUltimoEventoEm(), occurredAt);
            return false;
        }
        if (turma == null) {
            turma = new TurmaReplica(mensagem.turmaId());
        }
        copiar(turma, mensagem.codigo(), mensagem.nome(), mensagem.anoLetivo(), mensagem.turno(),
                mensagem.capacidade(), mensagem.disciplinas());
        turma.setRemovida(mensagem.acao() == Acao.REMOVIDA);
        turma.setUltimoEventoEm(occurredAt);
        turmaReplicaRepository.save(turma);
        log.debug("schedule.updated aplicado: turma {} ({})", mensagem.turmaId(), mensagem.acao());
        return true;
    }

    /**
     * Garante que a turma esteja na réplica. Se ainda não estiver (o evento não chegou), busca no
     * gestao-administrativa-service e grava o resultado. A chamada HTTP fica fora de transação,
     * para não segurar uma conexão do banco enquanto espera o outro serviço.
     *
     * @throws RecursoNaoEncontradoException se a turma não existir no outro serviço
     * @throws com.distrischool.gestao_escolar.common.ServicoIndisponivelException se o outro
     *         serviço estiver fora do ar ou com o circuito aberto
     */
    public void garantirNaReplica(Long turmaId) {
        if (turmaReplicaRepository.existsById(turmaId)) {
            return;
        }
        TurmaAdministrativaResponse resposta = administrativaClient.buscarTurma(turmaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma", turmaId));
        try {
            transactionTemplate.executeWithoutResult(status -> gravarDoFallback(turmaId, resposta));
        } catch (DataIntegrityViolationException ex) {
            // O consumidor gravou a mesma turma em paralelo; o estado vindo do evento prevalece
            log.debug("Turma {} gravada em paralelo pelo consumidor; fallback descartado", turmaId);
        }
    }

    /**
     * Turma da réplica com bloqueio pessimista, para regras que dependem do estado atual dela
     * (vagas). Roda na transação de quem chama; chame {@link #garantirNaReplica} antes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TurmaReplica obterComBloqueio(Long turmaId) {
        return turmaReplicaRepository.findByIdParaAtualizacao(turmaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma", turmaId));
    }

    /** Turma da réplica, sem bloqueio; chame {@link #garantirNaReplica} antes. */
    @Transactional(readOnly = true)
    public TurmaReplica obter(Long turmaId) {
        return turmaReplicaRepository.findById(turmaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma", turmaId));
    }

    /**
     * Turma não removida que oferta hoje a disciplina: pré-condição para registrar chamadas e
     * avaliações. Chame {@link #garantirNaReplica} antes.
     *
     * @throws RegraNegocioException se a turma foi removida ou não oferta a disciplina
     */
    @Transactional(readOnly = true)
    public TurmaReplica obterComOferta(Long turmaId, Long disciplinaId) {
        TurmaReplica turma = obter(turmaId);
        if (turma.isRemovida()) {
            throw new RegraNegocioException("A turma " + turma.getCodigo() + " foi removida");
        }
        if (!turma.ofertaDisciplina(disciplinaId)) {
            throw new RegraNegocioException("A disciplina " + disciplinaId + " não é ofertada na turma " + turma.getCodigo());
        }
        return turma;
    }

    private void gravarDoFallback(Long turmaId, TurmaAdministrativaResponse resposta) {
        if (turmaReplicaRepository.existsById(turmaId)) {
            return;
        }
        TurmaReplica turma = new TurmaReplica(turmaId);
        copiar(turma, resposta.codigo(), resposta.nome(), resposta.anoLetivo(), resposta.turno(),
                resposta.capacidade(), resposta.disciplinas() != null ? resposta.disciplinas() : List.of());
        // Sem ultimoEventoEm: qualquer schedule.updated que chegar depois prevalece sobre o fallback
        turmaReplicaRepository.saveAndFlush(turma);
        log.info("Turma {} gravada na réplica a partir do gestao-administrativa-service", turmaId);
    }

    /**
     * Copia o estado da turma. {@code disciplinas} é a lista completa: ofertas ausentes dela são
     * marcadas como removidas (nunca apagadas).
     */
    private static void copiar(TurmaReplica turma, String codigo, String nome, Integer anoLetivo, String turno,
                               Integer capacidade, List<DisciplinaOfertada> disciplinas) {
        turma.setCodigo(codigo);
        turma.setNome(nome);
        turma.setAnoLetivo(anoLetivo);
        turma.setTurno(turno);
        turma.setCapacidade(capacidade);

        Set<Long> ofertadas = new HashSet<>();
        for (DisciplinaOfertada d : disciplinas) {
            ofertadas.add(d.disciplinaId());
            TurmaDisciplinaReplica oferta = turma.disciplina(d.disciplinaId()).orElseGet(() -> {
                TurmaDisciplinaReplica nova = new TurmaDisciplinaReplica(turma, d.disciplinaId());
                turma.getDisciplinas().add(nova);
                return nova;
            });
            oferta.setCodigo(d.codigo());
            oferta.setNome(d.nome());
            oferta.setProfessorId(d.professorId());
            oferta.setRemovida(false);
        }
        turma.getDisciplinas().stream()
                .filter(oferta -> !ofertadas.contains(oferta.getId().getDisciplinaId()))
                .forEach(oferta -> oferta.setRemovida(true));
    }

    private static void validar(ScheduleUpdatedMessage m) {
        if (m == null || m.turmaId() == null || m.occurredAt() == null || m.acao() == null
                || m.codigo() == null || m.nome() == null || m.anoLetivo() == null || m.turno() == null
                || m.disciplinas() == null) {
            throw new MensagemInvalidaException("schedule.updated sem campo obrigatório: " + m);
        }
        for (DisciplinaOfertada d : m.disciplinas()) {
            if (d == null || d.disciplinaId() == null || d.codigo() == null || d.nome() == null) {
                throw new MensagemInvalidaException("schedule.updated com disciplina incompleta: turma " + m.turmaId());
            }
        }
    }
}
