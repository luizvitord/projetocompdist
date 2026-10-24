import { fireEvent, render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { MemoryRouter } from 'react-router-dom';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { AlunosPage } from '../../alunos/AlunosPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('registry flows', () => {
  it('lists alunos and creates one while surfacing backend field validation', async () => {
    server.use(
      http.get('/api/alunos', () => HttpResponse.json({ content: [{ id: 1, matricula: '2026001', nome: 'Ana', email: 'ana@escola.test', telefone: '9999', dataNascimento: '2010-01-01', endereco: 'Rua A' }], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } })),
      http.post('/api/alunos', () => HttpResponse.json({ status: 400, detail: 'Dados inválidos', erros: { nome: 'Informe o nome' } }, { status: 400 }))
    );
    render(<MemoryRouter><AlunosPage /></MemoryRouter>);
    expect(await screen.findByText('Ana')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Novo aluno' }));
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Nova aluna' } });
    fireEvent.change(screen.getByLabelText('Nascimento'), { target: { value: '2011-01-01' } });
    fireEvent.change(screen.getByLabelText('Endereço'), { target: { value: 'Rua B' } });
    fireEvent.change(screen.getByLabelText('Telefone'), { target: { value: '8888' } });
    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'nova@escola.test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(await screen.findByText('Informe o nome')).toBeVisible();
  });
});
