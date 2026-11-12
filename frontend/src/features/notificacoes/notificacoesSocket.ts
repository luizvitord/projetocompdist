import { Client, ReconnectionTimeMode } from '@stomp/stompjs';
import { getSession } from '../../services/session';
import type { Notificacao } from '../../types/notificacao';

export type NotificacoesSocketHandlers = {
  /** Chamado a cada conexão, inclusive nas reconexões. */
  onConectar: () => void;
  onNotificacao: (notificacao: Notificacao) => void;
};

export type NotificacoesSocket = { desconectar: () => void };

/** Fila do próprio usuário; o servidor não deixa assinar nenhuma outra. */
const DESTINO = '/user/queue/notificacoes';
const HEARTBEAT_MS = 10_000;
const RECONEXAO_INICIAL_MS = 1_000;
const RECONEXAO_MAXIMA_MS = 30_000;

/** Mesma origem da página: o nginx, o proxy do Vite ou o Ingress encaminham o /ws. */
function urlDoWebSocket() {
  const protocolo = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocolo}//${window.location.host}/ws`;
}

/**
 * Abre a conexão STOMP das notificações em tempo real.
 *
 * O navegador não envia cabeçalhos no handshake do WebSocket, então o token vai no frame CONNECT.
 * Se a conexão cair, o cliente tenta de novo sozinho, com espera crescente (1 s, 2 s, 4 s... até
 * 30 s). Antes de cada tentativa confere a sessão: sem sessão (logout ou token expirado), para de
 * tentar. Um frame ERROR do servidor (token recusado) também encerra as tentativas.
 */
export function conectarNotificacoes(handlers: NotificacoesSocketHandlers): NotificacoesSocket {
  const client = new Client({
    brokerURL: urlDoWebSocket(),
    heartbeatIncoming: HEARTBEAT_MS,
    heartbeatOutgoing: HEARTBEAT_MS,
    reconnectDelay: RECONEXAO_INICIAL_MS,
    maxReconnectDelay: RECONEXAO_MAXIMA_MS,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL
  });

  client.beforeConnect = () => {
    const token = getSession()?.token;
    if (!token) {
      void client.deactivate();
      return;
    }
    client.connectHeaders = { Authorization: `Bearer ${token}` };
  };

  client.onConnect = () => {
    client.subscribe(DESTINO, (frame) => {
      try {
        handlers.onNotificacao(JSON.parse(frame.body) as Notificacao);
      } catch {
        // Mensagem fora do formato: ignora; a lista vem da API REST de qualquer forma
      }
    });
    handlers.onConectar();
  };

  client.onStompError = () => {
    void client.deactivate();
  };

  client.activate();
  return { desconectar: () => { void client.deactivate(); } };
}
