import { fireEvent, render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { AvaliacoesPage } from '../AvaliacoesPage';
const server = setupServer(); beforeAll(() => server.listen({ onUnhandledRequest: 'error' })); afterEach(() => server.resetHandlers()); afterAll(() => server.close());
describe('grade entry', () => { it('posts a grade record and maps a row validation error', async () => { server.use(http.get('/api/avaliacoes', () => HttpResponse.json({ content: [{ id: 3, descricao: 'Prova', turmaId: 1, disciplinaId: 2, bimestre: 1 }], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } })), http.post('/api/avaliacoes/3/notas', () => HttpResponse.json({ status: 400, detail: 'Inválido', erros: { 'notas[0].valor': 'Valor acima do máximo' } }, { status: 400 }))); render(<AvaliacoesPage />); fireEvent.click(await screen.findByRole('button', { name: 'Lançar notas' })); fireEvent.change(screen.getByLabelText('ID da matrícula'), { target: { value: '5' } }); fireEvent.change(screen.getByLabelText('Valor'), { target: { value: '15' } }); fireEvent.click(screen.getByRole('button', { name: 'Salvar nota' })); expect(await screen.findByText('Valor acima do máximo')).toBeVisible(); }); });
