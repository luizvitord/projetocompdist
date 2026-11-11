package com.distrischool.notificacao.nomes;

import com.distrischool.notificacao.common.MensagemInvalidaException;
import com.distrischool.notificacao.nomes.ScheduleUpdatedMessage.DisciplinaOfertada;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Cache local de nomes de turmas e disciplinas, alimentado pelo {@code schedule.updated}. Os
 * eventos de notas, frequência e matrícula só trazem ids; os nomes daqui deixam o texto das
 * notificações legível sem chamar outro serviço por HTTP.
 * <p>
 * É a única classe que escreve em {@code turma_nome} e {@code disciplina_nome}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheNomesService implements CatalogoNomes {

    private final TurmaNomeRepository turmaNomeRepository;
    private final DisciplinaNomeRepository disciplinaNomeRepository;

    /**
     * Aplica um {@code schedule.updated}. Idempotente: faz upsert pela chave e ignora eventos com
     * {@code occurredAt} anterior ao último aplicado (reentrega ou entrega fora de ordem).
     * <p>
     * Uma turma removida continua no cache: notificações antigas ainda se referem a ela.
     *
     * @return {@code false} se o evento foi ignorado por ser antigo
     */
    @Transactional
    public boolean aplicar(ScheduleUpdatedMessage mensagem) {
        validar(mensagem);
        // O TIMESTAMPTZ guarda microssegundos e arredonda o resto: sem truncar aqui, a reentrega
        // do último evento pareceria mais antiga que o valor gravado
        Instant occurredAt = mensagem.occurredAt().truncatedTo(ChronoUnit.MICROS);

        TurmaNome turma = turmaNomeRepository.findByIdParaAtualizacao(mensagem.turmaId()).orElse(null);
        if (turma != null && occurredAt.isBefore(turma.getUltimoEventoEm())) {
            log.info("schedule.updated ignorado: turma {} já reflete o evento de {} (recebido evento de {})",
                    mensagem.turmaId(), turma.getUltimoEventoEm(), occurredAt);
            return false;
        }
        if (turma == null) {
            turma = new TurmaNome(mensagem.turmaId());
        }
        turma.atualizar(mensagem.codigo(), mensagem.nome(), occurredAt);
        // O flush faz uma inserção concorrente falhar aqui dentro; o binder tenta de novo
        turmaNomeRepository.saveAndFlush(turma);

        // Sempre na ordem dos ids: dois eventos com as mesmas disciplinas bloqueiam as linhas na
        // mesma ordem e não entram em deadlock
        List<DisciplinaOfertada> disciplinas = mensagem.disciplinas().stream()
                .sorted(Comparator.comparing(DisciplinaOfertada::disciplinaId))
                .toList();
        for (DisciplinaOfertada ofertada : disciplinas) {
            aplicarDisciplina(ofertada, occurredAt);
        }
        log.debug("schedule.updated aplicado ao cache de nomes: turma {}", mensagem.turmaId());
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> nomeTurma(Long turmaId) {
        return turmaNomeRepository.findById(turmaId).map(TurmaNome::getNome);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> nomeDisciplina(Long disciplinaId) {
        return disciplinaNomeRepository.findById(disciplinaId).map(DisciplinaNome::getNome);
    }

    /** A disciplina pode ter um nome mais novo, vindo do evento de outra turma. */
    private void aplicarDisciplina(DisciplinaOfertada ofertada, Instant occurredAt) {
        DisciplinaNome disciplina = disciplinaNomeRepository.findByIdParaAtualizacao(ofertada.disciplinaId())
                .orElse(null);
        if (disciplina != null && occurredAt.isBefore(disciplina.getUltimoEventoEm())) {
            return;
        }
        if (disciplina == null) {
            disciplina = new DisciplinaNome(ofertada.disciplinaId());
        }
        disciplina.atualizar(ofertada.nome(), occurredAt);
        disciplinaNomeRepository.saveAndFlush(disciplina);
    }

    private static void validar(ScheduleUpdatedMessage m) {
        if (m == null || m.turmaId() == null || m.occurredAt() == null || m.codigo() == null || m.nome() == null
                || m.disciplinas() == null) {
            throw new MensagemInvalidaException("schedule.updated sem campo obrigatório");
        }
        for (DisciplinaOfertada d : m.disciplinas()) {
            if (d == null || d.disciplinaId() == null || d.nome() == null) {
                throw new MensagemInvalidaException("schedule.updated com disciplina incompleta: turma " + m.turmaId());
            }
        }
    }
}
