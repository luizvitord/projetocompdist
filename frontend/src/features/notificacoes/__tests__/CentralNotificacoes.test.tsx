import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { FeedbackProvider } from '../../../components/feedback/FeedbackProvider';
import { clearSession, saveSession } from '../../../services/session';
import type { Notificacao } from '../../../types/notificacao';
import { CentralNotificacoes } from '../CentralNotificacoes';
import type { NotificacoesSocketHandlers } from '../notificacoesSocket';

// Cliente STOMP simulado: o teste dispara a conexão e a chegada de mensagens
const socket = vi.hoisted(() => ({
  handlers: null as NotificacoesSocketHandlers | null,
  conexoes: 0,
  desconectar: vi.fn()
}));
vi.mock('../notificacoesSocket', () => ({
  conectarNotificacoes: (handlers: NotificacoesSocketHandlers) => {
    socket.handlers = handlers;
    socket.conexoes += 1;
    return { desconectar: socket.desconectar };
  }
}));

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterAll(() => server.close());

const usuario = { id: 2, nome: 'Responsável', email: 'responsavel@distrischool.com', perfil: 'RESPONSAVEL' as const, referenciaId: 12 };

function notificacao(id: number, overrides: Partial<Notificacao> = {}): Notificacao {
  return {
    id, tipo: 'NOTA_LANCADA', titulo: 'Nova nota', mensagem: `Nova nota em Matemática (1º bimestre): ${id}`,
    link: '/alunos/12', criadaEm: '2026-11-10T11:00:00Z', lidaEm: null, lida: false, ...overrides
  };
}

function pagina(content: Notificacao[]) {
  return { content, page: { size: 10, number: 0, totalElements: content.length, totalPages: 1 } };
}

/** Contagem e lista servidas pela API; os testes trocam os valores antes de cada chamada. */
const api = { naoLidas: 3, lista: [notificacao(7), notificacao(6), notificacao(5, { lida: true, lidaEm: '2026-11-10T12:00:00Z' })] };

beforeEach(() => {
  socket.handlers = null;
  socket.conexoes = 0;
  socket.desconectar.mockClear();
  api.naoLidas = 3;
  api.lista = [notificacao(7), notificacao(6), notificacao(5, { lida: true, lidaEm: '2026-11-10T12:00:00Z' })];
  saveSession({ token: 'jwt', expiraEm: new Date(Date.now() + 3_600_000).toISOString(), usuario });
  server.use(
    http.get('/api/notificacoes/nao-lidas/contagem', () => HttpResponse.json({ total: api.naoLidas })),
    http.get('/api/notificacoes', () => HttpResponse.json(pagina(api.lista)))
  );
});

afterEach(() => {
  vi.useRealTimers();
  server.resetHandlers();
  clearSession();
});

function renderCentral() {
  return render(<FeedbackProvider>
    <MemoryRouter initialEntries={['/dashboard']}>
      <CentralNotificacoes />
      <Routes>
        <Route path="/dashboard" element={<p>Painel</p>} />
        <Route path="/alunos/:id" element={<h1>Detalhe do aluno</h1>} />
      </Routes>
    </MemoryRouter>
  </FeedbackProvider>);
}

async function conectar() {
  await act(async () => { socket.handlers?.onConectar(); });
}

async function abrirPainel() {
  fireEvent.click(screen.getByRole('button', { name: /^Notificações/ }));
  return screen.findByRole('dialog', { name: 'Notificações' });
}

describe('CentralNotificacoes', () => {
  it('shows the unread count fetched when the socket connects', async () => {
    renderCentral();
    expect(screen.getByRole('button', { name: 'Notificações' })).toBeVisible();
    expect(screen.queryByTestId('contagem-nao-lidas')).not.toBeInTheDocument();

    await conectar();

    expect(await screen.findByRole('button', { name: 'Notificações: 3 não lidas' })).toBeVisible();
    expect(screen.getByTestId('contagem-nao-lidas')).toHaveTextContent('3');
  });

  it('fetches the count again on every reconnection', async () => {
    renderCentral();
    await conectar();
    expect(await screen.findByRole('button', { name: 'Notificações: 3 não lidas' })).toBeVisible();

    // Chegaram duas enquanto a conexão estava caída
    api.naoLidas = 5;
    await conectar();

    expect(await screen.findByRole('button', { name: 'Notificações: 5 não lidas' })).toBeVisible();
  });

  it('lists the latest notifications when the panel is opened', async () => {
    const requests: string[] = [];
    server.use(http.get('/api/notificacoes', ({ request }) => {
      requests.push(new URL(request.url).search);
      return HttpResponse.json(pagina(api.lista));
    }));
    renderCentral();
    await conectar();

    const painel = await abrirPainel();

    const itens = await within(painel).findAllByRole('listitem');
    expect(itens).toHaveLength(3);
    expect(itens[0]).toHaveTextContent('Nova nota em Matemática (1º bimestre): 7');
    expect(within(itens[0]).getByRole('link', { name: 'Abrir' })).toHaveAttribute('href', '/alunos/12');
    // Só as não lidas têm a ação
    expect(within(painel).getAllByRole('button', { name: 'Marcar como lida' })).toHaveLength(2);
    expect(requests).toEqual(['?size=10']);
  });

  it('marks one notification as read', async () => {
    const marcadas: string[] = [];
    server.use(http.patch('/api/notificacoes/:id/lida', ({ params }) => {
      marcadas.push(String(params.id));
      return HttpResponse.json(notificacao(Number(params.id), { lida: true, lidaEm: '2026-11-10T13:00:00Z' }));
    }));
    renderCentral();
    await conectar();
    const painel = await abrirPainel();
    const primeira = (await within(painel).findAllByRole('listitem'))[0];

    fireEvent.click(within(primeira).getByRole('button', { name: 'Marcar como lida' }));

    expect(await screen.findByRole('button', { name: 'Notificações: 2 não lidas' })).toBeVisible();
    expect(marcadas).toEqual(['7']);
    expect(within(primeira).queryByRole('button', { name: 'Marcar como lida' })).not.toBeInTheDocument();
    expect(within(painel).getAllByRole('button', { name: 'Marcar como lida' })).toHaveLength(1);
  });

  it('marks all notifications as read', async () => {
    let chamadas = 0;
    server.use(http.post('/api/notificacoes/lidas', () => {
      chamadas += 1;
      return HttpResponse.json({ atualizadas: 3 });
    }));
    renderCentral();
    await conectar();
    const painel = await abrirPainel();
    await within(painel).findAllByRole('listitem');

    fireEvent.click(within(painel).getByRole('button', { name: 'Marcar todas como lidas' }));

    expect(await screen.findByRole('button', { name: 'Notificações' })).toBeVisible();
    expect(chamadas).toBe(1);
    expect(screen.queryByTestId('contagem-nao-lidas')).not.toBeInTheDocument();
    expect(within(painel).queryByRole('button', { name: 'Marcar como lida' })).not.toBeInTheDocument();
    expect(within(painel).getByRole('button', { name: 'Marcar todas como lidas' })).toBeDisabled();
  });

  it('shows a notification that arrives through the STOMP client', async () => {
    renderCentral();
    await conectar();
    const painel = await abrirPainel();
    await within(painel).findAllByRole('listitem');

    await act(async () => {
      socket.handlers?.onNotificacao(notificacao(8, { mensagem: 'Nova nota em Matemática (1º bimestre): 8,5' }));
    });

    expect(screen.getByRole('button', { name: 'Notificações: 4 não lidas' })).toBeVisible();
    // Aviso rápido pelo feedback global
    expect(screen.getByRole('status')).toHaveTextContent('Nova nota em Matemática (1º bimestre): 8,5');
    const itens = within(painel).getAllByRole('listitem');
    expect(itens).toHaveLength(4);
    expect(itens[0]).toHaveTextContent('Nova nota em Matemática (1º bimestre): 8,5');
  });

  it('counts a notification that arrives while the panel is closed and ignores a repeated one', async () => {
    renderCentral();
    await conectar();
    expect(await screen.findByRole('button', { name: 'Notificações: 3 não lidas' })).toBeVisible();

    await act(async () => { socket.handlers?.onNotificacao(notificacao(8)); });
    expect(screen.getByRole('button', { name: 'Notificações: 4 não lidas' })).toBeVisible();

    // Com a lista carregada, a mesma notificação entregue de novo não conta duas vezes
    api.naoLidas = 4;
    api.lista = [notificacao(8), ...api.lista];
    const painel = await abrirPainel();
    expect(await within(painel).findAllByRole('listitem')).toHaveLength(4);
    await act(async () => { socket.handlers?.onNotificacao(notificacao(8)); });

    expect(screen.getByRole('button', { name: 'Notificações: 4 não lidas' })).toBeVisible();
    expect(within(painel).getAllByRole('listitem')).toHaveLength(4);
  });

  it('opens the linked page, marks the notification as read and closes the panel', async () => {
    const marcadas: string[] = [];
    server.use(http.patch('/api/notificacoes/:id/lida', ({ params }) => {
      marcadas.push(String(params.id));
      return HttpResponse.json(notificacao(Number(params.id), { lida: true, lidaEm: '2026-11-10T13:00:00Z' }));
    }));
    renderCentral();
    await conectar();
    const painel = await abrirPainel();
    const primeira = (await within(painel).findAllByRole('listitem'))[0];

    fireEvent.click(within(primeira).getByRole('link', { name: 'Abrir' }));

    expect(await screen.findByRole('heading', { name: 'Detalhe do aluno' })).toBeVisible();
    expect(screen.queryByRole('dialog', { name: 'Notificações' })).not.toBeInTheDocument();
    await waitFor(() => expect(marcadas).toEqual(['7']));
  });

  it('shows an empty state and a recoverable error', async () => {
    api.lista = [];
    renderCentral();
    await conectar();
    let painel = await abrirPainel();
    expect(await within(painel).findByText('Nenhuma notificação por enquanto.')).toBeVisible();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Notificações' })).not.toBeInTheDocument();

    server.use(http.get('/api/notificacoes', () => HttpResponse.json({ detail: 'Indisponível' }, { status: 500 })));
    painel = await abrirPainel();
    expect(await within(painel).findByRole('alert')).toHaveTextContent('Não foi possível carregar as notificações.');

    server.use(http.get('/api/notificacoes', () => HttpResponse.json(pagina([notificacao(7)]))));
    fireEvent.click(within(painel).getByRole('button', { name: 'Tentar novamente' }));
    expect(await within(painel).findAllByRole('listitem')).toHaveLength(1);
  });

  it('connects once and disconnects when it leaves the screen (logout)', () => {
    const { unmount } = renderCentral();
    expect(socket.conexoes).toBe(1);
    expect(socket.desconectar).not.toHaveBeenCalled();

    unmount();

    expect(socket.desconectar).toHaveBeenCalled();
  });

  it('disconnects when the token expires', () => {
    vi.useFakeTimers();
    saveSession({ token: 'jwt', expiraEm: new Date(Date.now() + 60_000).toISOString(), usuario });
    renderCentral();

    vi.advanceTimersByTime(59_000);
    expect(socket.desconectar).not.toHaveBeenCalled();

    vi.advanceTimersByTime(2_000);
    expect(socket.desconectar).toHaveBeenCalledTimes(1);
  });
});
