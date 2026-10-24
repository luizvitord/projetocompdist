import { fireEvent, render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { MatriculasPage } from '../MatriculasPage';

const server = setupServer(); beforeAll(() => server.listen({ onUnhandledRequest: 'error' })); afterEach(() => server.resetHandlers()); afterAll(() => server.close());
describe('MatriculasPage', () => { it('creates an enrollment and surfaces a 409 status transition', async () => {
  server.use(http.get('/api/matriculas', () => HttpResponse.json({ content: [{ id: 4, alunoId: 1, turmaId: 2, anoLetivo: 2026, status: 'ATIVA', dataMatricula: '2026-01-01' }], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } })), http.patch('/api/matriculas/4/status', () => HttpResponse.json({ status: 409, detail: 'Matrícula já encerrada' }, { status: 409 })));
  render(<MatriculasPage />); expect(await screen.findByText('ATIVA')).toBeVisible(); fireEvent.click(screen.getByRole('button', { name: 'Status' })); fireEvent.change(screen.getByLabelText('Novo status'), { target: { value: 'TRANCADA' } }); fireEvent.click(screen.getByRole('button', { name: 'Confirmar' })); expect(await screen.findByText('Matrícula já encerrada')).toBeVisible();
}); });
