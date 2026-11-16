import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { AlunosPage } from '../../alunos/AlunosPage';
import { PessoasPage } from '../../pessoas/PessoasPage';
import { FeedbackProvider } from '../../../components/feedback/FeedbackProvider';

const semAcento = (texto: string) => texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
// 45 alunos; a "Ana Lúcia" é a 45ª em ordem de cadastro (fora da primeira página)
const alunos = Array.from({ length: 44 }, (_, i) => ({ id: i + 1, matricula: `2026${String(i + 1).padStart(6, '0')}`, nome: `Aluno ${String(i + 1).padStart(2, '0')}`, email: `aluno${i + 1}@escola.com`, telefone: '11988887777', dataNascimento: '2012-01-01', endereco: 'Rua A' }))
  .concat({ id: 45, matricula: '2026000045', nome: 'Ana Lúcia Prado', email: 'ana.lucia@exemplo.org', telefone: '11988887777', dataNascimento: '2012-01-01', endereco: 'Rua B' });
const requisicoes: URL[] = [];

/** Simula GET /api/alunos com busca (nome, matrícula, email; sem acento) e paginação. */
function listar(url: URL) {
  const busca = semAcento(url.searchParams.get('busca') ?? '');
  const filtrados = alunos.filter((a) => !busca || [a.nome, a.matricula, a.email].some((campo) => semAcento(campo).includes(busca)));
  const size = Number(url.searchParams.get('size') ?? 20); const page = Number(url.searchParams.get('page') ?? 0);
  return { content: filtrados.slice(page * size, (page + 1) * size), page: { size, number: page, totalElements: filtrados.length, totalPages: Math.ceil(filtrados.length / size) } };
}

const server = setupServer(http.get('/api/alunos', ({ request }) => { const url = new URL(request.url); requisicoes.push(url); return HttpResponse.json(listar(url)); }));
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { server.resetHandlers(); requisicoes.length = 0; });
afterAll(() => server.close());

function Local() { const local = useLocation(); return <output data-testid="local">{local.pathname}{local.search}</output>; }
function renderizar(url = '/alunos') {
  render(<FeedbackProvider><MemoryRouter initialEntries={[url]}><AlunosPage /><Local /></MemoryRouter></FeedbackProvider>);
  return screen.getByRole('searchbox', { name: 'Buscar alunos' });
}
const ultima = () => requisicoes[requisicoes.length - 1];
const local = () => screen.getByTestId('local').textContent;
const esperar = (ms: number) => act(() => new Promise((resolve) => setTimeout(resolve, ms)));

describe('busca nas listagens (RegistryPage)', () => {
  it('é acessível e diz o que é pesquisado', async () => {
    const campo = renderizar();
    expect(screen.getByRole('search', { name: 'Buscar alunos' })).toContainElement(campo);
    expect(campo).toHaveAttribute('placeholder', 'Buscar por nome, matrícula ou email');
    await screen.findByText('Aluno 01');
  });

  it('espera a pausa na digitação e faz uma única consulta com o parâmetro busca', async () => {
    const campo = renderizar();
    await screen.findByText('Aluno 01');
    expect(requisicoes).toHaveLength(1);

    for (const parcial of ['l', 'lu', 'luc', 'luci', 'lucia']) fireEvent.change(campo, { target: { value: parcial } });
    expect(await screen.findByText('Ana Lúcia Prado')).toBeVisible();
    await esperar(400);

    expect(requisicoes).toHaveLength(2);
    expect(ultima().searchParams.get('busca')).toBe('lucia');
    expect(ultima().searchParams.get('page')).toBe('0');
    expect(local()).toBe('/alunos?busca=lucia');
  });

  it('volta para a página 1 ao mudar a busca', async () => {
    const campo = renderizar();
    await screen.findByText('Aluno 01');
    fireEvent.click(screen.getByRole('button', { name: 'Próxima' }));
    await screen.findByText('Aluno 21');
    expect(local()).toBe('/alunos?pagina=2');

    fireEvent.change(campo, { target: { value: 'escola' } });

    await waitFor(() => expect(ultima().searchParams.get('busca')).toBe('escola'));
    expect(ultima().searchParams.get('page')).toBe('0');
    expect(await screen.findByText('Página 1 de 3 · 44 registros')).toBeVisible();
    expect(local()).toBe('/alunos?busca=escola');
  });

  it('mantém a busca e a página na URL ao recarregar ou voltar', async () => {
    const campo = renderizar('/alunos?busca=escola&pagina=2');

    expect(await screen.findByText('Aluno 21')).toBeVisible();
    expect(campo).toHaveValue('escola');
    expect(requisicoes[0].searchParams.get('busca')).toBe('escola');
    expect(requisicoes[0].searchParams.get('page')).toBe('1');
    expect(screen.getByText('Página 2 de 3 · 44 registros')).toBeVisible();
  });

  it('limpa a busca pelo botão ×', async () => {
    const campo = renderizar('/alunos?busca=lucia');
    await screen.findByText('Ana Lúcia Prado');

    fireEvent.click(screen.getByRole('button', { name: 'Limpar campo de busca' }));

    expect(campo).toHaveValue('');
    expect(await screen.findByText('Aluno 01')).toBeVisible();
    expect(ultima().searchParams.has('busca')).toBe(false);
    expect(local()).toBe('/alunos');
  });

  it('limpa a busca com Esc', async () => {
    const campo = renderizar('/alunos?busca=lucia');
    await screen.findByText('Ana Lúcia Prado');

    fireEvent.keyDown(campo, { key: 'Escape' });

    expect(campo).toHaveValue('');
    expect(await screen.findByText('Aluno 01')).toBeVisible();
    expect(local()).toBe('/alunos');
  });

  it('mostra "nenhum resultado" com a opção de limpar, diferente de "nenhum registro"', async () => {
    const campo = renderizar();
    await screen.findByText('Aluno 01');
    fireEvent.change(campo, { target: { value: 'inexistente' } });

    expect(await screen.findByText('Nenhum resultado para "inexistente"')).toBeVisible();
    expect(screen.queryByText('Nenhum aluno encontrado')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Limpar busca' }));

    expect(await screen.findByText('Aluno 01')).toBeVisible();
    expect(campo).toHaveValue('');
  });

  it('cancela a consulta anterior: uma resposta atrasada não sobrescreve a busca atual', async () => {
    server.use(http.get('/api/alunos', async ({ request }) => {
      const url = new URL(request.url); requisicoes.push(url);
      if (url.searchParams.get('busca') === 'aluno') await delay(600);
      return HttpResponse.json(listar(url));
    }));
    const campo = renderizar();
    await screen.findByText('Aluno 01');

    fireEvent.change(campo, { target: { value: 'aluno' } });
    await waitFor(() => expect(ultima().searchParams.get('busca')).toBe('aluno'));
    fireEvent.change(campo, { target: { value: 'ana' } });

    expect(await screen.findByText('Ana Lúcia Prado')).toBeVisible();
    await esperar(700);
    expect(screen.getByText('Ana Lúcia Prado')).toBeVisible();
    expect(screen.queryByText('Aluno 01')).not.toBeInTheDocument();
  });
});

describe('busca nas abas de Pessoas', () => {
  it('trocar de aba descarta a busca da outra', async () => {
    server.use(
      http.get('/api/professores', ({ request }) => { requisicoes.push(new URL(request.url)); return HttpResponse.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } }); }),
      http.get('/api/tecnicos', ({ request }) => { requisicoes.push(new URL(request.url)); return HttpResponse.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } }); })
    );
    render(<FeedbackProvider><MemoryRouter initialEntries={['/pessoas?busca=maria']}><PessoasPage /><Local /></MemoryRouter></FeedbackProvider>);
    expect(await screen.findByText('Nenhum resultado para "maria"')).toBeVisible();
    expect(screen.getByRole('searchbox', { name: 'Buscar professores' })).toHaveAttribute('placeholder', 'Buscar por nome, email ou qualificação');

    fireEvent.click(screen.getByRole('tab', { name: 'Técnicos' }));

    const campo = await screen.findByRole('searchbox', { name: 'Buscar técnicos' });
    expect(campo).toHaveValue('');
    expect(campo).toHaveAttribute('placeholder', 'Buscar por nome, email ou setor');
    expect(local()).toBe('/pessoas?aba=tecnicos');
    await waitFor(() => expect(ultima().pathname).toBe('/api/tecnicos'));
    expect(ultima().searchParams.has('busca')).toBe(false);
  });
});
