package com.distrischool.notificacao.notificacao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Substitui o {@link NotificacaoInsercaoRepository} nos testes sem banco, com a mesma regra do
 * índice único {@code uk_notificacao_evento_destinatario}: uma linha por (hash do evento,
 * destinatário); a segunda inserção é ignorada, sem erro.
 */
public class InsercaoEmMemoria extends NotificacaoInsercaoRepository {

    private final Set<String> indiceUnico = new HashSet<>();
    private final List<NotificacaoCriada> gravadas = new ArrayList<>();

    public InsercaoEmMemoria() {
        super(null);
    }

    @Override
    public Optional<NotificacaoCriada> inserirSeNova(String eventoHash, NovaNotificacao nova, Instant criadaEm) {
        if (!indiceUnico.add(eventoHash + "|" + nova.destinatario().nome())) {
            return Optional.empty();
        }
        NotificacaoCriada criada = new NotificacaoCriada((long) gravadas.size() + 1, nova.destinatario(), nova.tipo(),
                nova.titulo(), nova.mensagem(), nova.link(), criadaEm);
        gravadas.add(criada);
        return Optional.of(criada);
    }

    public List<NotificacaoCriada> gravadas() {
        return gravadas;
    }
}
