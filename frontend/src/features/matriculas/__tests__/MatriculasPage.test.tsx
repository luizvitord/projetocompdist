import { fireEvent, render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { MatriculasPage } from '../MatriculasPage';
import { FeedbackProvider } from '../../../components/feedback/FeedbackProvider';

const server = setupServer(); beforeAll(() => server.listen({ onUnhandledRequest: 'error' })); afterEach(() => server.resetHandlers()); afterAll(() => server.close());
describe('MatriculasPage', () => { it('shows turma names in the enrollment selector and surfaces a 409 status transition', async () => {
  server.use(http.get('/api/matriculas', () => HttpResponse.json({ content: [{ id: 4, matriculaAluno: '2026000001', alunoNome: 'Gabriel', turmaId: 2, anoLetivo: 2026, status: 'ATIVA', dataMatricula: '2026-01-01' }], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } })), http.get('/api/turmas', () => HttpResponse.json({ content: [{ id: 2, codigo: '9A-2026', nome: 'Turma A', anoLetivo: 2026, turno: 'MANHA', capacidade: 30, professores: [], disciplinas: [] }], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } })), http.get('/api/alunos', () => HttpResponse.json({ content: [{ id: 1, matricula: '2026000001', nome: 'Gabriel' }], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } })), http.patch('/api/matriculas/4/status', () => HttpResponse.json({ status: 409, detail: 'Matrícula já encerrada' }, { status: 409 })));
  render(<FeedbackProvider><MatriculasPage /></FeedbackProvider>); expect(await screen.findByText('ATIVA')).toBeVisible(); fireEvent.click(screen.getByRole('button', { name: 'Nova matrícula' })); expect(await screen.findByRole('option', { name: '9A-2026 — Turma A — 2026 — MANHA' })).toBeVisible(); expect(screen.getByRole('option', { name: 'Gabriel — 2026000001' })).toBeVisible(); fireEvent.click(screen.getByRole('button', { name: 'Fechar' })); fireEvent.click(screen.getByRole('button', { name: 'Status' })); fireEvent.change(screen.getByLabelText('Novo status'), { target: { value: 'TRANCADA' } }); fireEvent.click(screen.getByRole('button', { name: 'Confirmar' })); expect(await screen.findByText('Matrícula já encerrada')).toBeVisible();
}); });

describe('matrículas loading failures', () => {
  it('does not present an empty registry when the matrícula list fails to load', async () => {
    server.use(
      http.get('/api/matriculas', () => HttpResponse.json({ detail: 'Indisponível' }, { status: 500 })),
      http.get('/api/turmas', () => HttpResponse.json({ content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 0 } })),
      http.get('/api/alunos', () => HttpResponse.json({ content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 0 } }))
    );

    render(<FeedbackProvider><MatriculasPage /></FeedbackProvider>);

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar as matrículas.');
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();
    expect(screen.queryByText('Nenhuma matrícula encontrada')).not.toBeInTheDocument();
  });
});
