import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { api } from '../http';

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('gateway API client', () => {
  it('uses the /api boundary and returns decoded payloads', async () => {
    server.use(http.get('/api/alunos', () => HttpResponse.json({ content: [{ id: 7, nome: 'Ana' }], page: { size: 10, number: 0, totalElements: 1, totalPages: 1 } })));

    await expect(api.get<{ content: { id: number; nome: string }[] }>('/alunos')).resolves.toEqual(expect.objectContaining({ content: [{ id: 7, nome: 'Ana' }] }));
  });

  it('throws a 409 ApiProblem without changing caller state', async () => {
    server.use(http.patch('/api/matriculas/4/status', () => HttpResponse.json({ status: 409, detail: 'Matrícula já encerrada' }, { status: 409 })));
    const formValues = { status: 'TRANCADA', motivo: 'Mudança de turno' };

    await expect(api.patch('/matriculas/4/status', formValues)).rejects.toMatchObject({ status: 409, detail: 'Matrícula já encerrada' });
    expect(formValues).toEqual({ status: 'TRANCADA', motivo: 'Mudança de turno' });
  });
});
