import { fireEvent, render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { LoginPage } from '../LoginPage';
import { clearSession, getSession } from '../../../services/session';

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { server.resetHandlers(); clearSession(); });
afterAll(() => server.close());

function renderLogin() {
  const router = createMemoryRouter([
    { path: '/login', element: <LoginPage /> },
    { path: '/dashboard', element: <h1>Painel da coordenação</h1> }
  ], { initialEntries: ['/login'] });
  render(<RouterProvider router={router} />);
}

function fillAndSubmit(email: string, password: string) {
  fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: email } });
  fireEvent.change(screen.getByLabelText('Senha'), { target: { value: password } });
  fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
}

describe('LoginPage', () => {
  it('stores the session returned by the API and redirects', async () => {
    server.use(http.post('/api/auth/login', () => HttpResponse.json({
      token: 'jwt-token',
      expiraEm: new Date(Date.now() + 3_600_000).toISOString(),
      usuario: { id: 1, nome: 'Coordenação', email: 'coordenacao@distrischool.com', perfil: 'ADMIN', referenciaId: null }
    })));
    renderLogin();

    fillAndSubmit('coordenacao@distrischool.com', '123456');

    expect(await screen.findByRole('heading', { name: 'Painel da coordenação' })).toBeVisible();
    expect(getSession()?.token).toBe('jwt-token');
  });

  it('shows the API message and keeps the user on the page for invalid credentials', async () => {
    server.use(http.post('/api/auth/login', () =>
      HttpResponse.json({ status: 401, detail: 'Email ou senha inválidos' }, { status: 401 })));
    renderLogin();

    fillAndSubmit('coordenacao@distrischool.com', 'errada');

    expect(await screen.findByRole('alert')).toHaveTextContent('Email ou senha inválidos');
    expect(getSession()).toBeNull();
  });
});
