package com.distrischool.notificacao.evento;

import com.distrischool.notificacao.nomes.CacheNomesService;
import com.distrischool.notificacao.nomes.ScheduleUpdatedMessage;
import com.distrischool.notificacao.notificacao.NotificacaoCriada;
import com.distrischool.notificacao.notificacao.NotificacaoService;
import com.distrischool.notificacao.notificacao.NovaNotificacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Ponto de entrada de todos os eventos consumidos. O {@code schedule.updated} só atualiza o cache
 * de nomes; os demais geram notificações.
 * <p>
 * Qualquer exceção sai daqui para o binder, que tenta de novo e, por fim, envia o registro para a
 * DLT. Os logs trazem só tópico, chave e quantidades: os payloads têm dados de alunos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessadorEventos {

    private final MapeadorEventos mapeador;
    private final LeitorEventos leitor;
    private final CacheNomesService cacheNomes;
    private final NotificacaoService notificacaoService;

    public void processar(String topico, Message<byte[]> mensagem) {
        String chave = chave(mensagem);
        byte[] payload = mensagem.getPayload();

        if (KafkaTopics.SCHEDULE_UPDATED.equals(topico)) {
            cacheNomes.aplicar(leitor.ler(topico, payload, ScheduleUpdatedMessage.class));
            return;
        }
        List<NovaNotificacao> novas = mapeador.mapear(topico, payload);
        List<NotificacaoCriada> criadas = notificacaoService.registrar(EventoHash.calcular(topico, chave, payload), novas);
        log.debug("Evento do tópico {} (chave {}): {} notificações previstas, {} gravadas",
                topico, chave, novas.size(), criadas.size());
    }

    /** O binder entrega a chave como bytes; os produtores usam o id (turma, aluno...) como texto. */
    private static String chave(Message<?> mensagem) {
        Object chave = mensagem.getHeaders().get(KafkaHeaders.RECEIVED_KEY);
        if (chave instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return chave != null ? chave.toString() : "";
    }
}
