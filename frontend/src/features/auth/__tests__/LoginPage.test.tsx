import { fireEvent, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { LoginPage } from '../LoginPage';
import { hasDemoSession, signOut } from '../demoAuth';

describe('LoginPage', () => {
  afterEach(() => signOut());

  it('creates the demo session and redirects after valid credentials', async () => {
    const router = createMemoryRouter([
      { path: '/login', element: <LoginPage /> },
      { path: '/dashboard', element: <h1>Painel da coordenação</h1> }
    ], { initialEntries: ['/login'] });
    render(<RouterProvider router={router} />);

    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'coordenacao@distrischool.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('heading', { name: 'Painel da coordenação' })).toBeVisible();
    expect(hasDemoSession()).toBe(true);
  });
});
