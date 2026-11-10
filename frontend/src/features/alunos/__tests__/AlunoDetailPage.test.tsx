import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AlunoDetailPage } from '../AlunoDetailPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('AlunoDetailPage', () => {
  it('shows a recoverable error instead of loading forever when the student cannot be loaded', async () => {
    server.use(http.get('/api/alunos/42', () => HttpResponse.json({ detail: 'Indisponível' }, { status: 500 })));

    render(<MemoryRouter initialEntries={['/alunos/42']}><Routes><Route path="/alunos/:id" element={<AlunoDetailPage />} /></Routes></MemoryRouter>);

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar os dados do aluno.');
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();
    expect(screen.queryByText('Carregando aluno...')).not.toBeInTheDocument();
  });
});
