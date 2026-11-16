import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { MemoryRouter } from 'react-router-dom';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { MatriculasPage } from '../MatriculasPage';
import { FeedbackProvider } from '../../../components/feedback/FeedbackProvider';

const turmaA = { id: 2, codigo: '9A-2026', nome: 'Turma A', anoLetivo: 2026, turno: 'MANHA', capacidade: 30, professores: [], disciplinas: [] };
const turmaB = { id: 5, codigo: '9B-2026', nome: 'Turma B', anoLetivo: 2026, turno: 'TARDE', capacidade: 30, professores: [], disciplinas: [] };
const pagina = <T,>(content: T[]) => ({ content, page: { size: 10, number: 0, totalElements: content.length, totalPages: 1 } });
const matriculas = http.get('/api/matriculas', () => HttpResponse.json({ content: [{ id: 4, matriculaAluno: '2026000001', alunoNome: 'Gabriel', turmaId: 2, anoLetivo: 2026, status: 'ATIVA', dataMatricula: '2026-01-01' }], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } }));
const turmaPorId = http.get('/api/turmas/2', () => HttpResponse.json(turmaA));
const buscaTurmas = http.get('/api/turmas', () => HttpResponse.json(pagina([turmaA, turmaB])));
const buscaAlunos = http.get('/api/alunos', () => HttpResponse.json(pagina([{ id: 1, matricula: '2026000001', nome: 'Gabriel' }])));

const server = setupServer(); beforeAll(() => server.listen({ onUnhandledRequest: 'error' })); afterEach(() => server.resetHandlers()); afterAll(() => server.close());
const renderizar = () => render(<FeedbackProvider><MemoryRouter><MatriculasPage /></MemoryRouter></FeedbackProvider>);

describe('MatriculasPage', () => {
  it('shows the turma of each enrollment without loading a fixed page of turmas, and surfaces a 409 status transition', async () => {
    const turmasListadas: string[] = [];
    server.use(matriculas, turmaPorId, http.get('/api/turmas', ({ request }) => { turmasListadas.push(request.url); return HttpResponse.json(pagina([turmaA])); }), http.patch('/api/matriculas/4/status', () => HttpResponse.json({ status: 409, detail: 'Matrícula já encerrada' }, { status: 409 })));
    renderizar();
    expect(await screen.findByText('ATIVA')).toBeVisible();
    expect(await screen.findByText('9A-2026 — Turma A')).toBeVisible();
    expect(turmasListadas).toEqual([]);

    fireEvent.click(screen.getByRole('button', { name: 'Status' })); fireEvent.change(screen.getByLabelText('Novo status'), { target: { value: 'TRANCADA' } }); fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }));
    expect(await screen.findByText('Matrícula já encerrada')).toBeVisible();
  });

  it('creates an enrollment by searching the student and the turma', async () => {
    let payload: unknown;
    server.use(matriculas, turmaPorId, buscaTurmas, buscaAlunos, http.post('/api/matriculas', async ({ request }) => { payload = await request.json(); return HttpResponse.json({ id: 9 }, { status: 201 }); }));
    renderizar();
    fireEvent.click(await screen.findByRole('button', { name: 'Nova matrícula' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Nova matrícula' });

    const aluno = within(dialogo).getByRole('combobox', { name: 'Aluno' });
    fireEvent.focus(aluno); fireEvent.change(aluno, { target: { value: 'Gab' } });
    fireEvent.click(await within(dialogo).findByRole('option', { name: 'Gabriel — 2026000001' }));

    const turma = within(dialogo).getByRole('combobox', { name: 'Turma' });
    fireEvent.focus(turma); fireEvent.change(turma, { target: { value: '9B' } });
    fireEvent.click(await within(dialogo).findByRole('option', { name: '9B-2026 — Turma B — 2026 — TARDE' }));

    fireEvent.click(within(dialogo).getByRole('button', { name: 'Criar matrícula' }));
    await waitFor(() => expect(payload).toEqual({ matriculaAluno: '2026000001', turmaId: 5 }));
    expect(await screen.findByText('Matrícula criada com sucesso.')).toBeVisible();
  });

  it('transfers to a turma found by search, without suggesting the current one', async () => {
    let payload: unknown; let anoBuscado: string | null = null;
    server.use(matriculas, turmaPorId, http.get('/api/turmas', ({ request }) => { anoBuscado = new URL(request.url).searchParams.get('anoLetivo'); return HttpResponse.json(pagina([turmaA, turmaB])); }), http.post('/api/matriculas/4/transferencia', async ({ request }) => { payload = await request.json(); return HttpResponse.json({ id: 10 }, { status: 201 }); }));
    renderizar();
    fireEvent.click(await screen.findByRole('button', { name: 'Transferir' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Transferir matrícula' });

    const destino = within(dialogo).getByRole('combobox', { name: 'Turma de destino' });
    fireEvent.focus(destino);
    fireEvent.click(await within(dialogo).findByRole('option', { name: '9B-2026 — Turma B — 2026 — TARDE' }));
    expect(within(dialogo).queryByRole('option', { name: /9A-2026/ })).not.toBeInTheDocument();
    expect(anoBuscado).toBe('2026');

    fireEvent.change(within(dialogo).getByLabelText('Motivo'), { target: { value: 'Mudança de turno' } });
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Transferir' }));
    await waitFor(() => expect(payload).toEqual({ turmaDestinoId: 5, motivo: 'Mudança de turno' }));
  });
});

describe('matrículas loading failures', () => {
  it('does not present an empty registry when the matrícula list fails to load', async () => {
    server.use(http.get('/api/matriculas', () => HttpResponse.json({ detail: 'Indisponível' }, { status: 500 })));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar as matrículas.');
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();
    expect(screen.queryByText('Nenhuma matrícula encontrada')).not.toBeInTheDocument();
  });
});

describe('busca de matrículas', () => {
  it('envia a busca com a ordem mais recentes primeiro e mostra "nenhum resultado"', async () => {
    const pedidas: URL[] = [];
    server.use(turmaPorId, http.get('/api/matriculas', ({ request }) => {
      const url = new URL(request.url); pedidas.push(url);
      const lista = url.searchParams.get('busca') === 'zzz' ? [] : [{ id: 4, matriculaAluno: '2026000001', alunoNome: 'Gabriel', turmaId: 2, anoLetivo: 2026, status: 'ATIVA', dataMatricula: '2026-01-01' }];
      return HttpResponse.json({ content: lista, page: { size: 20, number: 0, totalElements: lista.length, totalPages: lista.length ? 1 : 0 } });
    }));
    renderizar();
    expect(await screen.findByText('Gabriel')).toBeVisible();
    expect(pedidas[0].searchParams.get('sort')).toBe('id,desc');
    const campo = screen.getByRole('searchbox', { name: 'Buscar matrículas' });
    expect(campo).toHaveAttribute('placeholder', 'Buscar por aluno, número de matrícula ou turma');

    fireEvent.change(campo, { target: { value: 'zzz' } });

    expect(await screen.findByText('Nenhum resultado para "zzz"')).toBeVisible();
    expect(screen.queryByText('Nenhuma matrícula encontrada')).not.toBeInTheDocument();
    expect(pedidas[pedidas.length - 1].searchParams.get('busca')).toBe('zzz');
    expect(pedidas[pedidas.length - 1].searchParams.get('page')).toBe('0');
  });
});
