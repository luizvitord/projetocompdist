package com.distrischool.gestao_escolar.event;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publica no Kafka os eventos gravados na outbox (transactional outbox).
 * <p>
 * Cada ciclo bloqueia um lote de pendentes com {@code FOR UPDATE SKIP LOCKED}: com várias réplicas,
 * cada uma publica linhas diferentes. O lote roda numa transação só; se o pod cair no meio, as linhas
 * voltam a ficar pendentes e são reenviadas. A entrega é "pelo menos uma vez": os consumidores
 * precisam ser idempotentes. Os logs trazem só id, tópico e chave, nunca o payload (dados de alunos).
 * <p>
 * Ordem por chave: os eventos de uma mesma chave são enviados em rodadas, um por vez, na ordem de
 * gravação; se um falhar, os seguintes da mesma chave ficam para o próximo ciclo. Chaves diferentes
 * seguem em paralelo e a falha de uma não impede as outras.
 * <p>
 * Kafka inacessível: o primeiro envio que esgota a espera por metadados ({@code max.block.ms}) conta
 * uma tentativa e encerra o ciclo; o resto do lote fica pendente sem contar tentativa. O ciclo inteiro
 * respeita {@code prazoLote}. Nenhum evento é descartado.
 */
@Slf4j
public class OutboxRelay {

    private static final int TAMANHO_MAXIMO_ERRO = 2000;

    private final OutboxRepository repository;
    private final KafkaOperations<String, byte[]> kafka;
    private final TransactionTemplate transactionTemplate;
    private final Configuracao configuracao;
    private final Clock clock;
    private final AtomicLong pendentes = new AtomicLong();

    /**
     * @param tamanhoLote máximo de eventos bloqueados por ciclo
     * @param prazoLote   tempo máximo de um ciclo (envios e confirmações); maior que {@code delivery.timeout.ms}
     * @param retencao    por quanto tempo um evento publicado continua na tabela
     */
    public record Configuracao(int tamanhoLote, Duration prazoLote, Duration retencao) {
    }

    public OutboxRelay(OutboxRepository repository, KafkaOperations<String, byte[]> kafka,
                       TransactionTemplate transactionTemplate, Configuracao configuracao, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.transactionTemplate = transactionTemplate;
        this.configuracao = configuracao;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.intervalo:1s}", initialDelayString = "${outbox.relay.intervalo:1s}")
    public void publicarPendentes() {
        transactionTemplate.executeWithoutResult(status -> publicarLote());
        pendentes.set(repository.contarPendentes());
    }

    @Scheduled(cron = "${outbox.limpeza.cron:0 0 3 * * *}")
    public void limparPublicados() {
        Instant limite = clock.instant().minus(configuracao.retencao());
        int apagados = repository.apagarPublicadosAntesDe(limite);
        if (apagados > 0) {
            log.info("{} eventos publicados antes de {} apagados da outbox", apagados, limite);
        }
    }

    /** Eventos pendentes na última contagem; exposto como a métrica {@code outbox.eventos.pendentes}. */
    public long pendentes() {
        return pendentes.get();
    }

    private void publicarLote() {
        List<OutboxEvento> lote = repository.bloquearPendentes(configuracao.tamanhoLote());
        if (lote.isEmpty()) {
            return;
        }
        long prazo = System.nanoTime() + configuracao.prazoLote().toNanos();

        // Uma fila por tópico + chave, na ordem de gravação (o lote vem ordenado por id)
        Map<String, Deque<OutboxEvento>> filas = new LinkedHashMap<>();
        for (OutboxEvento evento : lote) {
            filas.computeIfAbsent(evento.topico() + '\u0000' + evento.chave(), k -> new ArrayDeque<>()).add(evento);
        }

        boolean continuar = true;
        while (continuar && !filas.isEmpty()) {
            // Rodada: o próximo evento de cada chave, todos em paralelo
            Map<Deque<OutboxEvento>, CompletableFuture<?>> rodada = new LinkedHashMap<>();
            for (Deque<OutboxEvento> fila : filas.values()) {
                if (System.nanoTime() >= prazo) {
                    continuar = false;
                    break;
                }
                OutboxEvento evento = fila.peek();
                CompletableFuture<?> envio = enviar(evento);
                if (brokerInacessivel(envio)) {
                    registrarFalha(evento, causa(envio));
                    log.warn("Kafka inacessível; o restante do lote da outbox fica para o próximo ciclo");
                    fila.clear();
                    continuar = false;
                    break;
                }
                rodada.put(fila, envio);
            }

            for (Map.Entry<Deque<OutboxEvento>, CompletableFuture<?>> envio : rodada.entrySet()) {
                Deque<OutboxEvento> fila = envio.getKey();
                if (aguardarConfirmacao(fila.peek(), envio.getValue(), prazo)) {
                    fila.poll();
                } else {
                    // Os próximos eventos desta chave esperam este ser publicado
                    fila.clear();
                }
            }
            filas.values().removeIf(Deque::isEmpty);
        }
    }

    private CompletableFuture<?> enviar(OutboxEvento evento) {
        // O payload já é o JSON final: vai como bytes, sem nova serialização e sem cabeçalhos
        ProducerRecord<String, byte[]> registro = new ProducerRecord<>(
                evento.topico(), evento.chave(), evento.payload().getBytes(StandardCharsets.UTF_8));
        try {
            return kafka.send(registro);
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    private boolean aguardarConfirmacao(OutboxEvento evento, CompletableFuture<?> envio, long prazo) {
        try {
            envio.get(Math.max(0, prazo - System.nanoTime()), TimeUnit.NANOSECONDS);
            repository.marcarPublicado(evento.id());
            log.debug("Evento {} da outbox publicado no tópico {} (chave {})", evento.id(), evento.topico(), evento.chave());
            return true;
        } catch (TimeoutException ex) {
            registrarFalha(evento, new TimeoutException("Sem confirmação do Kafka dentro de " + configuracao.prazoLote()));
            return false;
        } catch (ExecutionException ex) {
            registrarFalha(evento, ex.getCause());
            return false;
        } catch (InterruptedException ex) {
            // Encerramento do pod: a transação é desfeita e o lote volta a ficar pendente
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicação da outbox interrompida", ex);
        }
    }

    private void registrarFalha(OutboxEvento evento, Throwable erro) {
        String mensagem = descrever(erro);
        repository.registrarFalha(evento.id(), mensagem);
        log.warn("Falha ao publicar o evento {} da outbox no tópico {} (chave {}, tentativa {}): {}",
                evento.id(), evento.topico(), evento.chave(), evento.tentativas() + 1, mensagem);
    }

    /** Envio que falhou na hora por esgotar a espera por metadados: o broker não está acessível. */
    private static boolean brokerInacessivel(CompletableFuture<?> envio) {
        if (!envio.isCompletedExceptionally()) {
            return false;
        }
        for (Throwable t = causa(envio); t != null; t = t.getCause()) {
            if (t instanceof org.apache.kafka.common.errors.TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static Throwable causa(CompletableFuture<?> envio) {
        try {
            envio.join();
            return null;
        } catch (CompletionException ex) {
            return ex.getCause();
        } catch (CancellationException ex) {
            return ex;
        }
    }

    /** Tipo e mensagem da exceção e de até 4 causas, sem stack trace; nunca contém o payload. */
    private static String descrever(Throwable erro) {
        StringBuilder texto = new StringBuilder();
        Throwable t = erro;
        for (int nivel = 0; t != null && nivel < 5; nivel++, t = t.getCause() != t ? t.getCause() : null) {
            if (nivel > 0) {
                texto.append(" <- ");
            }
            texto.append(t.getClass().getName());
            if (t.getMessage() != null) {
                texto.append(": ").append(t.getMessage());
            }
        }
        return texto.length() > TAMANHO_MAXIMO_ERRO ? texto.substring(0, TAMANHO_MAXIMO_ERRO) : texto.toString();
    }
}
