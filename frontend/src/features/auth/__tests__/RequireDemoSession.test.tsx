import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { RequireDemoSession } from '../RequireDemoSession';
import { LoginPage } from '../LoginPage';
import { signOut } from '../demoAuth';

describe('RequireDemoSession', () => {
  afterEach(() => signOut());

  it('redirects unauthenticated users to the login screen', async () => {
    const router = createMemoryRouter([
      { path: '/login', element: <LoginPage /> },
      {
        element: <RequireDemoSession />,
        children: [{ path: '/alunos', element: <p>Cadastro de alunos</p> }]
      }
    ], { initialEntries: ['/alunos'] });

    render(<RouterProvider router={router} />);

    expect(await screen.findByRole('heading', { name: 'Acesso da coordenação' })).toBeVisible();
    expect(screen.queryByText('Cadastro de alunos')).not.toBeInTheDocument();
  });
});
