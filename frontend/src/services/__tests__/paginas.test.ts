import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { buscarTodasAsPaginas } from '../paginas';

const alunos = Array.from({ length: 230 }, (_, i) => ({ id: i + 1, nome: `Aluno ${i + 1}` }));
const requisicoes: URL[] = [];
const server = setupServer(http.get('/api/alunos', ({ request }) => {
  const url = new URL(request.url); requisicoes.push(url);
  const size = Number(url.searchParams.get('size')); const page = Number(url.searchParams.get('page'));
  return HttpResponse.json({ content: alunos.slice(page * size, (page + 1) * size), page: { size, number: page, totalElements: alunos.length, totalPages: Math.ceil(alunos.length / size) } });
}));
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { server.resetHandlers(); requisicoes.length = 0; });
afterAll(() => server.close());

describe('buscarTodasAsPaginas', () => {
  it('carrega todas as páginas de uma turma com mais de 100 alunos', async () => {
    const todos = await buscarTodasAsPaginas<{ id: number }>('/alunos', { turmaId: 7, sort: ['nome,asc', 'id,asc'] });

    expect(todos).toHaveLength(230);
    expect(requisicoes.map((url) => url.searchParams.get('page'))).toEqual(['0', '1', '2']);
    expect(requisicoes[0].searchParams.get('turmaId')).toBe('7');
    expect(requisicoes[0].searchParams.getAll('sort')).toEqual(['nome,asc', 'id,asc']);
  });

  it('para quando a turma está vazia', async () => {
    server.use(http.get('/api/alunos', () => HttpResponse.json({ content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 0 } })));

    expect(await buscarTodasAsPaginas('/alunos', { turmaId: 1 })).toEqual([]);
  });
});
