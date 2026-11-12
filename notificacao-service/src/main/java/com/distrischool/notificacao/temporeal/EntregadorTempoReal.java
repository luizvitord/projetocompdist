package com.distrischool.notificacao.temporeal;

import com.distrischool.notificacao.notificacao.Destinatario;
import com.distrischool.notificacao.notificacao.NotificacaoMetricas;
import com.distrischool.notificacao.notificacao.NotificacaoResponse;
import com.distrischool.notificacao.notificacao.NotificacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

/**
 * Entrega em tempo real, com várias réplicas.
 * <p>
 * Os eventos de domínio são lidos em um grupo compartilhado (competing consumers): cada evento é
 * processado por uma réplica só, que pode não ser a réplica em que o usuário está conectado. Por
 * isso a réplica que grava a notificação publica um aviso em {@code notificacao.criada}
 * ({@link PublicadorNotificacaoCriada}), e todas as réplicas leem esse tópico em <b>grupo
 * anônimo</b> ({@link TempoRealStreamConfig}): cada instância recebe todos os avisos (fan-out) e
 * entrega só às sessões conectadas nela.
 * <p>
 * Um aviso perdido (réplica reiniciando, Kafka fora do ar) não é reentregue, e nem precisa: o
 * WebSocket é só o atalho. A fonte da verdade é o banco, e o frontend busca a contagem de não
 * lidas a cada conexão.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntregadorTempoReal {

    /** Fila do usuário; o cliente assina {@code /user/queue/notificacoes}. */
    static final String FILA = "/queue/notificacoes";

    private final SimpUserRegistry sessoes;
    private final SimpMessagingTemplate websocket;
    private final NotificacaoService notificacaoService;
    private final NotificacaoMetricas metricas;

    public void entregar(NotificacaoCriadaMensagem aviso) {
        if (aviso.id() == null || aviso.perfil() == null) {
            log.warn("Aviso de notificação fora do contrato ignorado");
            return;
        }
        Destinatario destinatario = aviso.destinatario();
        SimpUser usuario = sessoes.getUser(destinatario.nome());
        if (usuario == null || usuario.getSessions().isEmpty()) {
            // Ninguém conectado nesta réplica: nada a fazer (nem consulta ao banco)
            return;
        }
        // A busca confere o dono: um aviso nunca entrega a notificação de outro destinatário
        notificacaoService.buscarPorId(destinatario, aviso.id())
                .ifPresent(notificacao -> entregar(usuario, notificacao));
    }

    private void entregar(SimpUser usuario, NotificacaoResponse notificacao) {
        int sessoesDoUsuario = usuario.getSessions().size();
        websocket.convertAndSendToUser(usuario.getName(), FILA, notificacao);
        for (int i = 0; i < sessoesDoUsuario; i++) {
            metricas.enviadaPorWebsocket();
        }
        log.debug("Notificação {} entregue por WebSocket a {} ({} sessões)", notificacao.id(), usuario.getName(),
                sessoesDoUsuario);
    }
}
