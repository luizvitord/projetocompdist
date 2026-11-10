import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { MemoryRouter } from 'react-router-dom';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { AlunosPage } from '../../alunos/AlunosPage';
import { TurmasPage } from '../../turmas/TurmasPage';
import { PessoasPage } from '../../pessoas/PessoasPage';
import { FeedbackProvider } from '../../../components/feedback/FeedbackProvider';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('registry flows', () => {
  it('loads a registry only once when no filters are supplied', async () => {
    let requests = 0;
    server.use(http.get('/api/alunos', () => {
      requests += 1;
      return HttpResponse.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } });
    }));

    render(<FeedbackProvider><MemoryRouter><AlunosPage /></MemoryRouter></FeedbackProvider>);

    await screen.findByText('Nenhum aluno encontrado');
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(requests).toBe(1);
  });

  it('lists alunos and creates one while surfacing backend field validation', async () => {
    server.use(
      http.get('/api/alunos', () => HttpResponse.json({ content: [{ id: 1, matricula: '2026001', nome: 'Ana', email: 'ana@escola.test', telefone: '9999', dataNascimento: '2010-01-01', endereco: 'Rua A' }], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } })),
      http.post('/api/alunos', () => HttpResponse.json({ status: 400, detail: 'Dados inválidos', erros: { nome: 'Informe o nome' } }, { status: 400 }))
    );
    render(<FeedbackProvider><MemoryRouter><AlunosPage /></MemoryRouter></FeedbackProvider>);
    expect(await screen.findByText('Ana')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Novo aluno' }));
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Nova aluna' } });
    fireEvent.change(screen.getByLabelText('Nascimento'), { target: { value: '2011-01-01' } });
    fireEvent.change(screen.getByLabelText('Endereço'), { target: { value: 'Rua B' } });
    fireEvent.change(screen.getByLabelText('Telefone'), { target: { value: '85988887777' } });
    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'nova@escola.test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(await screen.findByText('Informe o nome')).toBeVisible();
  });

  it('does not show a save error after a successful student creation', async () => {
    let creations = 0;
    server.use(
      http.get('/api/alunos', () => HttpResponse.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } })),
      http.post('/api/alunos', () => {
        creations += 1;
        return HttpResponse.json({ id: 2, matricula: '2026002', nome: 'Nova aluna' }, { status: 201 });
      })
    );
    render(<FeedbackProvider><MemoryRouter><AlunosPage /></MemoryRouter></FeedbackProvider>);
    await screen.findByText('Nenhum aluno encontrado');
    fireEvent.click(screen.getByRole('button', { name: 'Novo aluno' }));
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Nova aluna' } });
    fireEvent.change(screen.getByLabelText('Nascimento'), { target: { value: '2011-01-01' } });
    fireEvent.change(screen.getByLabelText('Endereço'), { target: { value: 'Rua B' } });
    fireEvent.change(screen.getByLabelText('Telefone'), { target: { value: '85988887777' } });
    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'nova@escola.test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(creations).toBe(1));
    expect(await screen.findByRole('status')).toHaveTextContent('Aluno cadastrado(a) com sucesso.');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('blocks an invalid phone number before sending the student form', async () => {
    let creations = 0;
    server.use(
      http.get('/api/alunos', () => HttpResponse.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } })),
      http.post('/api/alunos', () => {
        creations += 1;
        return HttpResponse.json({ id: 2 }, { status: 201 });
      })
    );
    render(<FeedbackProvider><MemoryRouter><AlunosPage /></MemoryRouter></FeedbackProvider>);
    await screen.findByText('Nenhum aluno encontrado');
    fireEvent.click(screen.getByRole('button', { name: 'Novo aluno' }));
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Nova aluna' } });
    fireEvent.change(screen.getByLabelText('Nascimento'), { target: { value: '2011-01-01' } });
    fireEvent.change(screen.getByLabelText('Endereço'), { target: { value: 'Rua B' } });
    fireEvent.change(screen.getByLabelText('Telefone'), { target: { value: '12345' } });
    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'nova@escola.test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Informe um telefone válido com DDD.')).toBeVisible();
    expect(creations).toBe(0);
  });

  it('sends only telephone digits to the backend', async () => {
    let receivedPhone = '';
    server.use(
      http.get('/api/alunos', () => HttpResponse.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } })),
      http.post('/api/alunos', async ({ request }) => {
        receivedPhone = String((await request.json() as { telefone: string }).telefone);
        return HttpResponse.json({ id: 2, matricula: '2026002', nome: 'Nova aluna' }, { status: 201 });
      })
    );
    render(<FeedbackProvider><MemoryRouter><AlunosPage /></MemoryRouter></FeedbackProvider>);
    await screen.findByText('Nenhum aluno encontrado');
    fireEvent.click(screen.getByRole('button', { name: 'Novo aluno' }));
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Nova aluna' } });
    fireEvent.change(screen.getByLabelText('Nascimento'), { target: { value: '2011-01-01' } });
    fireEvent.change(screen.getByLabelText('Endereço'), { target: { value: 'Rua B' } });
    fireEvent.change(screen.getByLabelText('Telefone'), { target: { value: '85 98888-7777' } });
    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'nova@escola.test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(receivedPhone).toBe('85988887777'));
  });

  it('formats a Brazilian telephone number while it is typed', async () => {
    server.use(http.get('/api/alunos', () => HttpResponse.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } })));
    render(<FeedbackProvider><MemoryRouter><AlunosPage /></MemoryRouter></FeedbackProvider>);
    await screen.findByText('Nenhum aluno encontrado');
    fireEvent.click(screen.getByRole('button', { name: 'Novo aluno' }));
    const telefone = screen.getByLabelText('Telefone');
    fireEvent.change(telefone, { target: { value: '85988887777' } });

    expect(telefone).toHaveValue('(85) 98888-7777');
  });

  it('clears the formatted telephone after a successful student creation', async () => {
    server.use(
      http.get('/api/alunos', () => HttpResponse.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } })),
      http.post('/api/alunos', () => HttpResponse.json({ id: 2, matricula: '2026002', nome: 'Nova aluna' }, { status: 201 }))
    );
    render(<FeedbackProvider><MemoryRouter><AlunosPage /></MemoryRouter></FeedbackProvider>);
    await screen.findByText('Nenhum aluno encontrado');
    fireEvent.click(screen.getByRole('button', { name: 'Novo aluno' }));
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Nova aluna' } });
    fireEvent.change(screen.getByLabelText('Nascimento'), { target: { value: '2011-01-01' } });
    fireEvent.change(screen.getByLabelText('Endereço'), { target: { value: 'Rua B' } });
    fireEvent.change(screen.getByLabelText('Telefone'), { target: { value: '85988887777' } });
    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'nova@escola.test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    await screen.findByRole('status');
    fireEvent.click(screen.getByRole('button', { name: 'Novo aluno' }));

    expect(screen.getByLabelText('Telefone')).toHaveValue('');
  });

  it('offers only backend-compatible turno values', async () => {
    server.use(http.get('/api/turmas', () => HttpResponse.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } })));
    render(<FeedbackProvider><MemoryRouter><TurmasPage /></MemoryRouter></FeedbackProvider>);
    await screen.findByText('Nenhum turma encontrado');
    fireEvent.click(screen.getByRole('button', { name: 'Novo turma' }));

    const turno = screen.getByLabelText('Turno');
    expect(turno.tagName).toBe('SELECT');
    expect(Array.from((turno as HTMLSelectElement).options).map((option) => option.value).filter(Boolean)).toEqual(['MANHA', 'TARDE', 'NOITE', 'INTEGRAL']);
  });

  it('offers backend cargo enums and validates an invalid CPF before posting', async () => {
    let creations = 0;
    server.use(http.get('/api/professores', () => HttpResponse.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } })), http.get('/api/tecnicos', () => HttpResponse.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } })), http.post('/api/tecnicos', () => {
      creations += 1;
      return HttpResponse.json({ id: 1 }, { status: 201 });
    }));
    render(<FeedbackProvider><MemoryRouter><PessoasPage /></MemoryRouter></FeedbackProvider>);
    await screen.findByText('Nenhum professor encontrado');
    fireEvent.click(screen.getByRole('button', { name: 'Técnicos' }));
    await screen.findByText('Nenhum técnico encontrado');
    fireEvent.click(screen.getByRole('button', { name: 'Novo técnico' }));

    const cargo = screen.getByLabelText('Cargo');
    expect(cargo.tagName).toBe('SELECT');
    expect(Array.from((cargo as HTMLSelectElement).options).map((option) => option.value).filter(Boolean)).toEqual(['SECRETARIO', 'COORDENADOR', 'BIBLIOTECARIO', 'TECNICO_TI', 'AUXILIAR_ADMINISTRATIVO', 'OUTRO']);
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Técnico' } });
    fireEvent.change(screen.getByLabelText('CPF'), { target: { value: '12345678901' } });
    fireEvent.change(cargo, { target: { value: 'SECRETARIO' } });
    fireEvent.change(screen.getByLabelText('Setor'), { target: { value: 'Coordenação' } });
    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'tecnico@escola.test' } });
    fireEvent.change(screen.getByLabelText('Telefone'), { target: { value: '85988887777' } });
    fireEvent.change(screen.getByLabelText('Admissão'), { target: { value: '2026-01-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Informe um CPF válido.')).toBeVisible();
    expect(creations).toBe(0);
  });
});
