import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { AvaliacoesPage } from '../AvaliacoesPage';
import { FeedbackProvider } from '../../../components/feedback/FeedbackProvider';

type AvaliacaoApi = { id: number; descricao: string; turmaId: number; disciplinaId: number; bimestre: number; tipo: string; peso: number; notaMaxima: number; data: string };

// 45 avaliações antigas (fevereiro) e a "Avaliação Teste", a mais recente (id e data maiores).
// Na ordem padrão da API (data ascendente) ela é a última e ficava fora da primeira página.
function base(): AvaliacaoApi[] {
  const antigas = Array.from({ length: 45 }, (_, i) => ({ id: i + 1, descricao: `Prova ${String(i + 1).padStart(2, '0')}`, turmaId: 1, disciplinaId: 2, bimestre: (i % 4) + 1, tipo: 'PROVA', peso: 1, notaMaxima: 10, data: `2026-02-${String((i % 28) + 1).padStart(2, '0')}` }));
  return [...antigas, { id: 46, descricao: 'Avaliação Teste', turmaId: 1, disciplinaId: 2, bimestre: 4, tipo: 'PROVA', peso: 1, notaMaxima: 10, data: '2026-11-10' }];
}
let avaliacoes = base();
const requisicoes: URL[] = [];
const turma = { id: 1, codigo: '9A-2026', nome: '9º Ano A', anoLetivo: 2026, turno: 'MANHA', capacidade: 30, professores: [], disciplinas: [{ disciplinaId: 2, codigo: 'MAT', nome: 'Matemática', professorId: null, professorNome: null }] };

/** Simula GET /api/avaliacoes com filtros, ordenação (sort repetido) e paginação, como o Spring Data. */
function listar(url: URL) {
  const sorts = url.searchParams.getAll('sort');
  const filtrados = avaliacoes.filter((a) => ['turmaId', 'disciplinaId', 'bimestre'].every((campo) => !url.searchParams.get(campo) || String(a[campo as keyof AvaliacaoApi]) === url.searchParams.get(campo)));
  const ordenados = [...filtrados].sort((a, b) => {
    for (const sort of sorts.length ? sorts : ['data,asc']) {
      const [campo, direcao] = sort.split(',');
      const va = a[campo as keyof AvaliacaoApi]; const vb = b[campo as keyof AvaliacaoApi];
      if (va !== vb) return (va < vb ? -1 : 1) * (direcao === 'desc' ? -1 : 1);
    }
    return 0;
  });
  const size = Number(url.searchParams.get('size') ?? 20); const page = Number(url.searchParams.get('page') ?? 0);
  return { content: ordenados.slice(page * size, (page + 1) * size), page: { size, number: page, totalElements: ordenados.length, totalPages: Math.ceil(ordenados.length / size) } };
}

const server = setupServer(
  http.get('/api/avaliacoes', ({ request }) => { const url = new URL(request.url); requisicoes.push(url); return HttpResponse.json(listar(url)); }),
  http.get('/api/turmas/1', () => HttpResponse.json(turma)),
  http.get('/api/turmas', () => HttpResponse.json({ content: [turma], page: { size: 10, number: 0, totalElements: 1, totalPages: 1 } }))
);
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { server.resetHandlers(); requisicoes.length = 0; avaliacoes = base(); });
afterAll(() => server.close());

const renderizar = () => render(<FeedbackProvider><AvaliacoesPage /></FeedbackProvider>);
const primeiraLinha = () => screen.getAllByRole('row')[1];

describe('AvaliacoesPage — paginação e ordem', () => {
  it('mostra a avaliação mais recente no topo, que na ordem padrão ficava fora da primeira página', async () => {
    renderizar();

    expect(await screen.findByText('Avaliação Teste')).toBeVisible();
    expect(within(primeiraLinha()).getByText('Avaliação Teste')).toBeVisible();
    expect(requisicoes[0].searchParams.getAll('sort')).toEqual(['data,desc', 'id,desc']);
    // Sem o sort, a primeira página (20 itens, data ascendente) não traria a avaliação
    const padrao = listar(new URL('http://x/api/avaliacoes?page=0&size=20'));
    expect(padrao.content.map((a) => a.descricao)).not.toContain('Avaliação Teste');
  });

  it('pagina com o total de registros', async () => {
    renderizar();
    await screen.findByText('Avaliação Teste');
    expect(screen.getByText('Página 1 de 3 · 46 registros')).toBeVisible();

    fireEvent.click(screen.getByRole('button', { name: 'Próxima' }));

    expect(await screen.findByText('Página 2 de 3 · 46 registros')).toBeVisible();
    expect(screen.queryByText('Avaliação Teste')).not.toBeInTheDocument();
    expect(requisicoes[requisicoes.length - 1].searchParams.get('page')).toBe('1');
  });

  it('filtra por bimestre e volta para a página 1', async () => {
    renderizar();
    await screen.findByText('Avaliação Teste');
    fireEvent.click(screen.getByRole('button', { name: 'Próxima' }));
    await screen.findByText('Página 2 de 3 · 46 registros');

    fireEvent.change(screen.getByLabelText('Filtrar por bimestre'), { target: { value: '4' } });

    expect(await screen.findByText(/Página 1 de 1 · 12 registros/)).toBeVisible();
    const ultima = requisicoes[requisicoes.length - 1];
    expect(ultima.searchParams.get('bimestre')).toBe('4');
    expect(ultima.searchParams.get('page')).toBe('0');
  });

  it('filtra por turma e disciplina', async () => {
    renderizar();
    await screen.findByText('Avaliação Teste');
    const campoTurma = screen.getByRole('combobox', { name: 'Filtrar por turma' });
    fireEvent.focus(campoTurma);
    fireEvent.click(await screen.findByRole('option', { name: '9A-2026 — 9º Ano A — 2026 — MANHA' }));
    await waitFor(() => expect(requisicoes[requisicoes.length - 1].searchParams.get('turmaId')).toBe('1'));

    fireEvent.change(screen.getByLabelText('Filtrar por disciplina'), { target: { value: '2' } });

    await waitFor(() => expect(requisicoes[requisicoes.length - 1].searchParams.get('disciplinaId')).toBe('2'));
  });

  it('depois de criar volta para a página 1 com a avaliação salva visível, mesmo com data antiga', async () => {
    server.use(http.post('/api/avaliacoes', async ({ request }) => {
      const corpo = await request.json() as Omit<AvaliacaoApi, 'id'>;
      const criada = { ...corpo, id: 47 };
      avaliacoes.push(criada);
      return HttpResponse.json(criada, { status: 201 });
    }));
    renderizar();
    await screen.findByText('Avaliação Teste');
    fireEvent.click(screen.getByRole('button', { name: 'Próxima' }));
    await screen.findByText('Página 2 de 3 · 46 registros');

    fireEvent.click(screen.getByRole('button', { name: 'Nova avaliação' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Nova avaliação' });
    const campoTurma = within(dialogo).getByRole('combobox', { name: 'Turma da avaliação' });
    fireEvent.focus(campoTurma);
    fireEvent.click(await within(dialogo).findByRole('option', { name: '9A-2026 — 9º Ano A — 2026 — MANHA' }));
    fireEvent.change(within(dialogo).getByLabelText('Disciplina da avaliação'), { target: { value: '2' } });
    fireEvent.change(within(dialogo).getByLabelText('Descrição'), { target: { value: 'Recuperação de fevereiro' } });
    fireEvent.change(within(dialogo).getByLabelText('Data'), { target: { value: '2026-02-01' } });
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Salvar avaliação' }));

    expect(await screen.findByText('Página 1 de 3 · 47 registros')).toBeVisible();
    expect(within(primeiraLinha()).getByText('Recuperação de fevereiro')).toBeVisible();
    expect(within(primeiraLinha()).getByText('salva agora')).toBeVisible();
  });
});
