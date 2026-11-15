import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { RequireAuth } from '../RequireAuth';
import { RequireCoordenacao, RequirePortal } from '../RequirePerfil';
import { clearSession, saveSession } from '../../../services/session';
import type { Perfil } from '../../../types/auth';

function login(perfil: Perfil, referenciaId: number | null = 12) {
  saveSession({
    token: 'jwt', expiraEm: new Date(Date.now() + 60_000).toISOString(),
    usuario: { id: 5, nome: 'Usuário', email: 'usuario@distrischool.com', perfil, referenciaId }
  });
}

function renderAt(path: string) {
  const router = createMemoryRouter([
    { path: '/login', element: <h1>Tela de login</h1> },
    { element: <RequireAuth />, children: [
      { element: <RequirePortal />, children: [{ path: '/portal', element: <h1>Portal do aluno</h1> }] },
      { element: <RequireCoordenacao />, children: [
        { path: '/dashboard', element: <h1>Visão geral</h1> },
        { path: '/alunos', element: <h1>Cadastro de alunos</h1> },
        { path: '/turmas/:id', element: <h1>Detalhe da turma</h1> }
      ] }
    ] }
  ], { initialEntries: [path] });
  const view = render(<RouterProvider router={router} />);
  return { router, view };
}

describe('route guards by profile', () => {
  afterEach(() => clearSession());

  it.each(['ALUNO', 'RESPONSAVEL'] as const)('sends %s from the coordination routes to /portal', async (perfil) => {
    login(perfil);
    for (const path of ['/dashboard', '/alunos', '/turmas/3']) {
      const { router, view } = renderAt(path);

      expect(await screen.findByRole('heading', { name: 'Portal do aluno' })).toBeVisible();
      expect(router.state.location.pathname).toBe('/portal');
      view.unmount();
    }
  });

  it('keeps an account without referenciaId on the portal too', async () => {
    login('ALUNO', null);
    const { router } = renderAt('/alunos');

    expect(await screen.findByRole('heading', { name: 'Portal do aluno' })).toBeVisible();
    expect(router.state.location.pathname).toBe('/portal');
  });

  it.each(['ADMIN', 'PROFESSOR'] as const)('lets %s use the coordination routes and sends them away from /portal', async (perfil) => {
    login(perfil, perfil === 'ADMIN' ? null : 3);
    const { view } = renderAt('/alunos');
    expect(await screen.findByRole('heading', { name: 'Cadastro de alunos' })).toBeVisible();
    view.unmount();

    const { router } = renderAt('/portal');
    expect(await screen.findByRole('heading', { name: 'Visão geral' })).toBeVisible();
    expect(router.state.location.pathname).toBe('/dashboard');
  });

  it('still asks for login before checking the profile', async () => {
    renderAt('/portal');

    expect(await screen.findByRole('heading', { name: 'Tela de login' })).toBeVisible();
  });
});
