import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../../app/routes';
import { FeedbackProvider } from '../../../components/feedback/FeedbackProvider';
import { clearSession, saveSession } from '../../../services/session';
import type { Perfil } from '../../../types/auth';

vi.mock('../../notificacoes/notificacoesSocket', () => ({
  conectarNotificacoes: () => ({ desconectar: () => undefined })
}));

const atual = new Date().getFullYear();
const aluno = { id: 12, matricula: '2026000012', nome: 'Ana Souza', dataNascimento: '2012-03-04', endereco: 'Rua A, 1', telefone: '11999990000', email: 'ana@example.com' };
const matricula = (id: number, anoLetivo: number) => ({
  id, alunoId: 12, matriculaAluno: aluno.matricula, alunoNome: aluno.nome, turmaId: 3, anoLetivo, status: 'ATIVA', dataMatricula: `${anoLetivo}-02-01`
});

/** Every URL the page asked for, to prove it only reads the linked student. */
let pedidos: string[] = [];

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterAll(() => server.close());

beforeEach(() => {
  pedidos = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    pedidos.push(url.pathname + url.search);
  });
  server.use(
    http.get('/api/notificacoes/nao-lidas/contagem', () => HttpResponse.json({ total: 0 })),
    http.get('/api/alunos/12', () => HttpResponse.json(aluno)),
    http.get('/api/matriculas', () => HttpResponse.json({
      content: [matricula(1, atual - 1), matricula(2, atual)],
      page: { size: 100, number: 0, totalElements: 2, totalPages: 1 }
    })),
    http.get('/api/alunos/12/boletim', ({ request }) => HttpResponse.json({
      situacao: 'EM_ANDAMENTO',
      disciplinas: [{ nome: `Matemática ${new URL(request.url).searchParams.get('anoLetivo')}`, mediaAnual: 8.5, situacao: 'APROVADO' }]
    })),
    http.get('/api/frequencias/alunos/12', () => HttpResponse.json({ geral: { percentual: 92.5, abaixoDoMinimo: false } }))
  );
});

afterEach(() => {
  server.events.removeAllListeners();
  server.resetHandlers();
  clearSession();
});

function login(perfil: Perfil, referenciaId: number | null) {
  saveSession({
    token: 'jwt', expiraEm: new Date(Date.now() + 3_600_000).toISOString(),
    usuario: { id: 5, nome: 'Usuário do portal', email: 'portal@distrischool.com', perfil, referenciaId }
  });
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<FeedbackProvider><RouterProvider router={router} /></FeedbackProvider>);
  return router;
}

const pedidosDeDados = () => pedidos.filter((pedido) => !pedido.startsWith('/api/notificacoes'));

describe('PortalPage', () => {
  it("shows the student's own data, boletim and frequencia to ALUNO", async () => {
    login('ALUNO', 12);
    renderAt('/portal');

    expect(await screen.findByRole('heading', { name: 'Meu portal' })).toBeVisible();
    expect(screen.getByText(`Ana Souza · Matrícula ${aluno.matricula}`)).toBeVisible();
    expect(await screen.findByRole('heading', { name: `Meu boletim (${atual})` })).toBeVisible();
    expect(await screen.findByText(`Matemática ${atual}: média 8.5 — APROVADO`)).toBeVisible();
    expect(screen.getByRole('heading', { name: `Minha frequência (${atual})` })).toBeVisible();
    expect(await screen.findByText('92.5% — Frequência adequada')).toBeVisible();
    // The coordination sidebar is not there; the notification bell is
    expect(screen.queryByRole('navigation', { name: 'Navegação principal' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Notificações/ })).toBeVisible();

    expect(pedidosDeDados()).toEqual(expect.arrayContaining([
      '/api/alunos/12', '/api/matriculas?alunoId=12&size=100',
      `/api/alunos/12/boletim?anoLetivo=${atual}`, `/api/frequencias/alunos/12?anoLetivo=${atual}`]));
    expect(pedidosDeDados().every((pedido) => /^\/api\/(alunos\/12(\/boletim)?\?|alunos\/12$|matriculas\?alunoId=12&|frequencias\/alunos\/12\?)/.test(pedido))).toBe(true);
  });

  it('switches the school year using the enrollments of the student', async () => {
    login('ALUNO', 12);
    renderAt('/portal');

    const seletor = await screen.findByRole('combobox', { name: 'Ano letivo' });
    expect([...seletor.querySelectorAll('option')].map((opcao) => opcao.textContent)).toEqual([String(atual), String(atual - 1)]);

    fireEvent.change(seletor, { target: { value: String(atual - 1) } });

    expect(await screen.findByRole('heading', { name: `Meu boletim (${atual - 1})` })).toBeVisible();
    expect(await screen.findByText(`Matemática ${atual - 1}: média 8.5 — APROVADO`)).toBeVisible();
    await waitFor(() => expect(pedidos).toContain(`/api/frequencias/alunos/12?anoLetivo=${atual - 1}`));
  });

  it('names the student for RESPONSAVEL', async () => {
    login('RESPONSAVEL', 12);
    renderAt('/portal');

    expect(await screen.findByRole('heading', { name: 'Acompanhamento de Ana Souza' })).toBeVisible();
    expect(await screen.findByRole('heading', { name: `Boletim de Ana Souza (${atual})` })).toBeVisible();
    expect(screen.getByRole('heading', { name: `Frequência de Ana Souza (${atual})` })).toBeVisible();
  });

  it('sends ALUNO and RESPONSAVEL from a coordination URL to the portal', async () => {
    login('RESPONSAVEL', 12);
    const router = renderAt('/alunos/13');

    expect(await screen.findByRole('heading', { name: 'Acompanhamento de Ana Souza' })).toBeVisible();
    expect(router.state.location.pathname).toBe('/portal');
    expect(pedidos.some((pedido) => pedido.includes('/13'))).toBe(false);
  });

  it('only tells an account without referenciaId to contact the school', async () => {
    login('ALUNO', null);
    renderAt('/portal');

    expect(await screen.findByRole('alert')).toHaveTextContent('Procure a coordenação da escola');
    expect(screen.queryByRole('combobox', { name: 'Ano letivo' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /boletim/i })).not.toBeInTheDocument();
    expect(pedidosDeDados()).toEqual([]);
  });

  it('explains when the student has no enrollment yet', async () => {
    login('ALUNO', 12);
    server.use(http.get('/api/matriculas', () => HttpResponse.json({ content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 0 } })));
    renderAt('/portal');

    expect(await screen.findByText(/Nenhuma matrícula encontrada/)).toBeVisible();
    expect(screen.queryByRole('heading', { name: /boletim/i })).not.toBeInTheDocument();
  });
});
