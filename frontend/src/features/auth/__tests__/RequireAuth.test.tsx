import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { RequireAuth } from '../RequireAuth';
import { clearSession, saveSession } from '../../../services/session';

function renderProtectedRoute() {
  const router = createMemoryRouter([
    { path: '/login', element: <h1>Tela de login</h1> },
    { element: <RequireAuth />, children: [{ path: '/alunos', element: <p>Cadastro de alunos</p> }] }
  ], { initialEntries: ['/alunos'] });
  render(<RouterProvider router={router} />);
}

const usuario = { id: 1, nome: 'Coordenação', email: 'coordenacao@distrischool.com', perfil: 'ADMIN' as const, referenciaId: null };

describe('RequireAuth', () => {
  afterEach(() => clearSession());

  it('redirects unauthenticated users to the login screen', async () => {
    renderProtectedRoute();

    expect(await screen.findByRole('heading', { name: 'Tela de login' })).toBeVisible();
    expect(screen.queryByText('Cadastro de alunos')).not.toBeInTheDocument();
  });

  it('redirects when the stored token has expired', async () => {
    saveSession({ token: 'old', expiraEm: new Date(Date.now() - 1000).toISOString(), usuario });
    renderProtectedRoute();

    expect(await screen.findByRole('heading', { name: 'Tela de login' })).toBeVisible();
  });

  it('renders the protected page for a valid session', async () => {
    saveSession({ token: 'jwt', expiraEm: new Date(Date.now() + 60_000).toISOString(), usuario });
    renderProtectedRoute();

    expect(await screen.findByText('Cadastro de alunos')).toBeVisible();
  });
});
