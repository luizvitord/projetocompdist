package com.distrischool.notificacao.notificacao;

import com.distrischool.notificacao.common.RecursoNaoEncontradoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.distrischool.notificacao.notificacao.NotificacaoSpecifications.comId;
import static com.distrischool.notificacao.notificacao.NotificacaoSpecifications.doDestinatario;
import static com.distrischool.notificacao.notificacao.NotificacaoSpecifications.naoLida;

/**
 * Grava as notificações geradas pelos eventos e atende as consultas do destinatário.
 * <p>
 * Os logs trazem só ids, tipo e destinatário (perfil e id de referência): título e mensagem têm
 * dados de alunos.
 */
@Slf4j
@Service
public class NotificacaoService {

    private final NotificacaoRepository repository;
    private final NotificacaoInsercaoRepository insercao;
    private final NotificacaoMetricas metricas;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public NotificacaoService(NotificacaoRepository repository, NotificacaoInsercaoRepository insercao,
                              NotificacaoMetricas metricas, ApplicationEventPublisher eventPublisher,
                              PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.insercao = insercao;
        this.metricas = metricas;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Grava, numa transação só, as notificações de um evento. Idempotente: se o mesmo evento
     * ({@code eventoHash}) for entregue de novo, nenhuma linha é duplicada.
     * <p>
     * Só depois do commit as notificações realmente gravadas são contadas na métrica e publicadas
     * como {@link NotificacaoCriada} (evento do Spring), para a entrega em tempo real. Uma falha
     * nessa entrega não desfaz a gravação: a notificação aparece no próximo carregamento da lista.
     *
     * @return as notificações gravadas agora (vazio se o evento era uma reentrega)
     */
    public List<NotificacaoCriada> registrar(String eventoHash, List<NovaNotificacao> novas) {
        if (novas.isEmpty()) {
            return List.of();
        }
        // O TIMESTAMPTZ guarda microssegundos: o valor devolvido aqui é igual ao que fica gravado
        Instant agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<NotificacaoCriada> criadas = transactionTemplate.execute(status -> {
            List<NotificacaoCriada> gravadas = new ArrayList<>();
            for (NovaNotificacao nova : novas) {
                insercao.inserirSeNova(eventoHash, nova, agora).ifPresent(gravadas::add);
            }
            return gravadas;
        });
        for (NotificacaoCriada criada : criadas) {
            metricas.criada(criada.tipo());
            log.debug("Notificação {} ({}) criada para {}", criada.id(), criada.tipo(), criada.destinatario().nome());
            eventPublisher.publishEvent(criada);
        }
        return criadas;
    }

    @Transactional(readOnly = true)
    public Page<NotificacaoResponse> buscar(Destinatario destinatario, boolean somenteNaoLidas, Pageable pageable) {
        Specification<Notificacao> filtro = doDestinatario(destinatario);
        if (somenteNaoLidas) {
            filtro = filtro.and(naoLida());
        }
        return repository.findAll(filtro, pageable).map(NotificacaoResponse::from);
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(Destinatario destinatario) {
        return repository.count(doDestinatario(destinatario).and(naoLida()));
    }

    /**
     * @throws RecursoNaoEncontradoException se a notificação não existir ou for de outro
     *         destinatário (a resposta é a mesma, para não revelar a existência dela)
     */
    @Transactional
    public NotificacaoResponse marcarComoLida(Destinatario destinatario, Long id) {
        Notificacao notificacao = repository.findOne(comId(id).and(doDestinatario(destinatario)))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Notificação", id));
        notificacao.marcarComoLida(clock.instant());
        return NotificacaoResponse.from(notificacao);
    }

    /** @return quantas notificações passaram a lidas */
    @Transactional
    public int marcarTodasComoLidas(Destinatario destinatario) {
        Instant agora = clock.instant();
        return destinatario.referenciaId() == null
                ? repository.marcarTodasComoLidasSemReferencia(destinatario.perfil(), agora)
                : repository.marcarTodasComoLidas(destinatario.perfil(), destinatario.referenciaId(), agora);
    }
}
