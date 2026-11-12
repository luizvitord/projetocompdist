import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearSession, saveSession } from '../../../services/session';
import { conectarNotificacoes } from '../notificacoesSocket';

type FakeClient = {
  brokerURL: string;
  heartbeatIncoming: number;
  heartbeatOutgoing: number;
  reconnectDelay: number;
  maxReconnectDelay: number;
  reconnectTimeMode: number;
  connectHeaders: Record<string, string>;
  beforeConnect: () => void;
  onConnect: () => void;
  onStompError: () => void;
  activate: ReturnType<typeof vi.fn>;
  deactivate: ReturnType<typeof vi.fn>;
  subscribe: ReturnType<typeof vi.fn>;
};

// Biblioteca STOMP simulada: guarda a configuração e os callbacks que o módulo registra
const stomp = vi.hoisted(() => ({ clients: [] as unknown[] }));
vi.mock('@stomp/stompjs', () => ({
  ReconnectionTimeMode: { LINEAR: 0, EXPONENTIAL: 1 },
  Client: class {
    connectHeaders = {};
    activate = vi.fn();
    deactivate = vi.fn(() => Promise.resolve());
    subscribe = vi.fn();
    constructor(config: Record<string, unknown>) {
      Object.assign(this, config);
      stomp.clients.push(this);
    }
  }
}));

const usuario = { id: 2, nome: 'Responsável', email: 'responsavel@distrischool.com', perfil: 'RESPONSAVEL' as const, referenciaId: 12 };

function abrir() {
  const handlers = { onConectar: vi.fn(), onNotificacao: vi.fn() };
  const socket = conectarNotificacoes(handlers);
  const client = stomp.clients[stomp.clients.length - 1] as FakeClient;
  return { handlers, socket, client };
}

describe('conectarNotificacoes', () => {
  beforeEach(() => {
    stomp.clients.length = 0;
    saveSession({ token: 'jwt-do-login', expiraEm: new Date(Date.now() + 3_600_000).toISOString(), usuario });
  });
  afterEach(() => clearSession());

  it('connects to /ws on the same origin with 10 s heartbeats and growing reconnection delay', () => {
    const { client } = abrir();

    expect(client.brokerURL).toBe(`ws://${window.location.host}/ws`);
    expect(client.heartbeatIncoming).toBe(10_000);
    expect(client.heartbeatOutgoing).toBe(10_000);
    expect(client.reconnectDelay).toBe(1_000);
    expect(client.maxReconnectDelay).toBe(30_000);
    expect(client.reconnectTimeMode).toBe(1);
    expect(client.activate).toHaveBeenCalledTimes(1);
  });

  it('sends the current token in the CONNECT frame of every attempt', () => {
    const { client } = abrir();

    client.beforeConnect();
    expect(client.connectHeaders).toEqual({ Authorization: 'Bearer jwt-do-login' });

    // Novo login na mesma aba: a reconexão usa o token novo
    saveSession({ token: 'jwt-novo', expiraEm: new Date(Date.now() + 3_600_000).toISOString(), usuario });
    client.beforeConnect();
    expect(client.connectHeaders).toEqual({ Authorization: 'Bearer jwt-novo' });
    expect(client.deactivate).not.toHaveBeenCalled();
  });

  it('stops reconnecting when there is no session anymore', () => {
    const { client } = abrir();
    clearSession();

    client.beforeConnect();

    expect(client.deactivate).toHaveBeenCalledTimes(1);
    expect(client.connectHeaders).toEqual({});
  });

  it('stops reconnecting when the token has expired', () => {
    const { client } = abrir();
    saveSession({ token: 'jwt-vencido', expiraEm: new Date(Date.now() - 1_000).toISOString(), usuario });

    client.beforeConnect();

    expect(client.deactivate).toHaveBeenCalledTimes(1);
  });

  it('subscribes to the user queue and reports each connection', () => {
    const { client, handlers } = abrir();

    client.onConnect();
    client.onConnect();

    expect(client.subscribe).toHaveBeenCalledTimes(2);
    expect(client.subscribe.mock.calls[0][0]).toBe('/user/queue/notificacoes');
    expect(handlers.onConectar).toHaveBeenCalledTimes(2);
  });

  it('delivers parsed notifications and ignores malformed messages', () => {
    const { client, handlers } = abrir();
    client.onConnect();
    const aoReceber = client.subscribe.mock.calls[0][1] as (frame: { body: string }) => void;

    aoReceber({ body: JSON.stringify({ id: 7, tipo: 'NOTA_LANCADA', mensagem: 'Nova nota' }) });
    aoReceber({ body: '{ quebrado' });

    expect(handlers.onNotificacao).toHaveBeenCalledTimes(1);
    expect(handlers.onNotificacao).toHaveBeenCalledWith({ id: 7, tipo: 'NOTA_LANCADA', mensagem: 'Nova nota' });
  });

  it('gives up when the server answers with an ERROR frame (token refused)', () => {
    const { client } = abrir();

    client.onStompError();

    expect(client.deactivate).toHaveBeenCalledTimes(1);
  });

  it('disconnects on demand', () => {
    const { client, socket } = abrir();

    socket.desconectar();

    expect(client.deactivate).toHaveBeenCalledTimes(1);
  });
});
