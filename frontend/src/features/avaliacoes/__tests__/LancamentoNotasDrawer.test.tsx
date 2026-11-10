import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { AvaliacoesPage } from '../AvaliacoesPage';
import { FeedbackProvider } from '../../../components/feedback/FeedbackProvider';
const server = setupServer(); beforeAll(() => server.listen({ onUnhandledRequest: 'error' })); afterEach(() => server.resetHandlers()); afterAll(() => server.close());
describe('grade entry', () => { it('loads enrolled students and posts grades using their institutional registrations', async () => { let payload: unknown; server.use(
  http.get('/api/avaliacoes', () => HttpResponse.json({ content: [{ id: 3, descricao: 'Prova', turmaId: 1, disciplinaId: 2, bimestre: 1 }], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } })),
  http.get('/api/turmas', () => HttpResponse.json({ content: [{ id: 1, codigo: '9A', nome: 'Turma A', anoLetivo: 2026, turno: 'MANHA', capacidade: 30, professores: [], disciplinas: [{ disciplinaId: 2, codigo: 'MAT', nome: 'Matemática', professorId: null, professorNome: null }] }], page: { size: 100, number: 0, totalElements: 1, totalPages: 1 } })),
  http.get('/api/alunos', () => HttpResponse.json({ content: [{ id: 99, matricula: '202600001', nome: 'Gabriel' }], page: { size: 100, number: 0, totalElements: 1, totalPages: 1 } })),
  http.post('/api/avaliacoes/3/notas', async ({ request }) => { payload = await request.json(); return HttpResponse.json({ id: 1 }, { status: 201 }); })
); render(<FeedbackProvider><AvaliacoesPage /></FeedbackProvider>); fireEvent.click(await screen.findByRole('button', { name: 'Lançar notas' })); await screen.findByText('Gabriel — 202600001'); fireEvent.change(screen.getByLabelText('Nota de Gabriel'), { target: { value: '8.5' } }); fireEvent.click(screen.getByRole('button', { name: 'Salvar notas' })); await waitFor(() => expect(payload).toEqual([{ matriculaAluno: '202600001', valor: 8.5 }])); }); });

describe('evaluation list loading failures', () => {
  it('offers a retry instead of loading evaluations forever after an API failure', async () => {
    server.use(
      http.get('/api/avaliacoes', () => HttpResponse.json({ detail: 'Indisponível' }, { status: 500 })),
      http.get('/api/turmas', () => HttpResponse.json({ content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 0 } }))
    );
    render(<FeedbackProvider><AvaliacoesPage /></FeedbackProvider>);

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar as avaliações.');
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();
    expect(screen.queryByText('Carregando avaliações...')).not.toBeInTheDocument();
  });
});
