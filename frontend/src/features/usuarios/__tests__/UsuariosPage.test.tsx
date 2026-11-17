import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { FeedbackProvider } from '../../../components/feedback/FeedbackProvider';
import { clearSession, saveSession } from '../../../services/session';
import { UsuariosPage } from '../UsuariosPage';

const server = setupServer();
const paginaVazia = { content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } };

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { server.resetHandlers(); clearSession(); });
afterAll(() => server.close());

function renderPage() {
  return render(<FeedbackProvider><UsuariosPage /></FeedbackProvider>);
}

describe('UsuariosPage', () => {
  it('creates a RESPONSAVEL with the selected student reference', async () => {
    let received: unknown;
    server.use(
      http.get('/api/usuarios', () => HttpResponse.json(paginaVazia)),
      http.post('/api/usuarios', async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ id: 9, nome: 'Responsável da Ana' }, { status: 201 });
      })
    );
    renderPage();
    await screen.findByText('Nenhum usuário encontrado');

    fireEvent.click(screen.getByRole('button', { name: 'Novo usuário' }));
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Responsável da Ana' } });
    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'responsavel@escola.test' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'segredo1' } });
    fireEvent.change(screen.getByLabelText('Perfil'), { target: { value: 'RESPONSAVEL' } });
    fireEvent.change(screen.getByLabelText(/ID do aluno vinculado/), { target: { value: '12' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cadastrar usuário' }));

    await waitFor(() => expect(received).toEqual({ nome: 'Responsável da Ana', email: 'responsavel@escola.test', senha: 'segredo1', perfil: 'RESPONSAVEL', referenciaId: 12 }));
  });

  it('updates an ADMIN without password or reference', async () => {
    let received: unknown;
    server.use(
      http.get('/api/usuarios', () => HttpResponse.json({ content: [{ id: 3, nome: 'Coordenação', email: 'coord@escola.test', perfil: 'ADMIN', referenciaId: null, ativo: true }], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } })),
      http.put('/api/usuarios/3', async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ id: 3, nome: 'Nova coordenação' });
      })
    );
    renderPage();
    expect(await screen.findByText('Coordenação')).toBeVisible();

    fireEvent.click(screen.getByRole('button', { name: 'Editar' }));
    expect(screen.queryByLabelText('Senha')).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/ID do aluno vinculado/)).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Nova coordenação' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }));

    await waitFor(() => expect(received).toEqual({ nome: 'Nova coordenação', email: 'coord@escola.test', perfil: 'ADMIN', referenciaId: null }));
  });

  it('resets a user password through the dedicated endpoint', async () => {
    let received: unknown;
    server.use(
      http.get('/api/usuarios', () => HttpResponse.json({ content: [{ id: 3, nome: 'Coordenação', email: 'coord@escola.test', perfil: 'ADMIN', referenciaId: null, ativo: true }], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } })),
      http.patch('/api/usuarios/3/senha', async ({ request }) => {
        received = await request.json();
        return new HttpResponse(null, { status: 204 });
      })
    );
    renderPage();
    expect(await screen.findByText('Coordenação')).toBeVisible();

    fireEvent.click(screen.getByRole('button', { name: 'Alterar senha' }));
    fireEvent.change(screen.getByLabelText('Nova senha'), { target: { value: 'nova-senha-segura' } });
    fireEvent.click(screen.getByRole('button', { name: 'Redefinir senha' }));

    await waitFor(() => expect(received).toEqual({ senha: 'nova-senha-segura' }));
  });

  it('does not offer deactivation for the current account', async () => {
    saveSession({ token: 'token', expiraEm: '2099-01-01T00:00:00Z', usuario: { id: 3, nome: 'Coordenação', email: 'coord@escola.test', perfil: 'ADMIN', referenciaId: null } });
    server.use(
      http.get('/api/usuarios', () => HttpResponse.json({ content: [{ id: 3, nome: 'Coordenação', email: 'coord@escola.test', perfil: 'ADMIN', referenciaId: null, ativo: true }], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } }))
    );
    renderPage();
    expect(await screen.findByText('Coordenação')).toBeVisible();

    expect(screen.queryByRole('button', { name: 'Desativar' })).not.toBeInTheDocument();
  });
});
