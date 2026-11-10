import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { FeedbackProvider } from '../../../components/feedback/FeedbackProvider';
import { TurmaDetailPage } from '../TurmaDetailPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('TurmaDetailPage', () => {
  it('shows a retryable error when the turma cannot be loaded', async () => {
    server.use(
      http.get('/api/turmas/7', () => HttpResponse.json({ detail: 'Indisponível' }, { status: 500 })),
      http.get('/api/disciplinas', () => HttpResponse.json({ content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 0 } })),
      http.get('/api/professores', () => HttpResponse.json({ content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 0 } }))
    );
    render(<FeedbackProvider><MemoryRouter initialEntries={['/turmas/7']}><Routes><Route path="/turmas/:id" element={<TurmaDetailPage />} /></Routes></MemoryRouter></FeedbackProvider>);

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar a turma.');
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();
    expect(screen.queryByText('Carregando turma...')).not.toBeInTheDocument();
  });
});
