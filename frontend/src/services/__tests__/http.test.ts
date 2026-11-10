import { delay, http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { api, NETWORK_MESSAGE, TIMEOUT_MESSAGE, UNAVAILABLE_MESSAGE } from '../http';
import { clearSession, saveSession } from '../session';

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

/** 503 that asks the client to retry immediately, so the tests do not wait for the backoff. */
const unavailable = () => HttpResponse.json({ status: 503, detail: 'Circuito aberto' }, { status: 503, headers: { 'Retry-After': '0' } });

describe('gateway API client', () => {
  it('uses the /api boundary and returns decoded payloads', async () => {
    server.use(http.get('/api/alunos', () => HttpResponse.json({ content: [{ id: 7, nome: 'Ana' }], page: { size: 10, number: 0, totalElements: 1, totalPages: 1 } })));

    await expect(api.get<{ content: { id: number; nome: string }[] }>('/alunos')).resolves.toEqual(expect.objectContaining({ content: [{ id: 7, nome: 'Ana' }] }));
  });

  it('sends the session token as a bearer header', async () => {
    saveSession({
      token: 'jwt-token',
      expiraEm: new Date(Date.now() + 60_000).toISOString(),
      usuario: { id: 1, nome: 'Coordenação', email: 'coordenacao@distrischool.com', perfil: 'ADMIN', referenciaId: null }
    });
    let authorization: string | null = null;
    server.use(http.get('/api/turmas', ({ request }) => {
      authorization = request.headers.get('Authorization');
      return HttpResponse.json({});
    }));

    await api.get('/turmas');

    expect(authorization).toBe('Bearer jwt-token');
    clearSession();
  });

  it('throws a 409 ApiProblem without changing caller state', async () => {
    server.use(http.patch('/api/matriculas/4/status', () => HttpResponse.json({ status: 409, detail: 'Matrícula já encerrada' }, { status: 409 })));
    const formValues = { status: 'TRANCADA', motivo: 'Mudança de turno' };

    await expect(api.patch('/matriculas/4/status', formValues)).rejects.toMatchObject({ status: 409, detail: 'Matrícula já encerrada' });
    expect(formValues).toEqual({ status: 'TRANCADA', motivo: 'Mudança de turno' });
  });
});

describe('timeout', () => {
  it('gives up on a slow response and does not retry it', async () => {
    let calls = 0;
    server.use(http.get('/api/relatorio', async () => {
      calls += 1;
      await delay(1_000);
      return HttpResponse.json({});
    }));

    await expect(api.get('/relatorio', { timeoutMs: 50 })).rejects.toMatchObject({ name: 'ApiProblem', status: 0, detail: TIMEOUT_MESSAGE });
    expect(calls).toBe(1);
  });

  it('keeps cancellation by the caller as an AbortError', async () => {
    server.use(http.get('/api/relatorio', async () => {
      await delay(1_000);
      return HttpResponse.json({});
    }));
    const controller = new AbortController();

    const pending = api.get('/relatorio', { signal: controller.signal });
    controller.abort();

    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
  });

  it('stops waiting for a retry when the caller cancels', async () => {
    server.use(http.get('/api/turmas', () => HttpResponse.json({}, { status: 503, headers: { 'Retry-After': '5' } })));
    const controller = new AbortController();

    const pending = api.get('/turmas', { signal: controller.signal });
    setTimeout(() => controller.abort(), 50);

    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
  });
});

describe('retry', () => {
  it('retries GET on 502/503/504 and returns the recovered response', async () => {
    for (const status of [502, 503, 504]) {
      let calls = 0;
      server.use(http.get('/api/turmas', () => {
        calls += 1;
        return calls === 1
          ? HttpResponse.json({}, { status, headers: { 'Retry-After': '0' } })
          : HttpResponse.json({ content: [] });
      }));

      await expect(api.get('/turmas')).resolves.toEqual({ content: [] });
      expect(calls).toBe(2);
    }
  });

  it('gives up after two retries with the friendly unavailability message', async () => {
    let calls = 0;
    server.use(http.get('/api/turmas', () => {
      calls += 1;
      return unavailable();
    }));

    await expect(api.get('/turmas')).rejects.toMatchObject({ status: 503, detail: UNAVAILABLE_MESSAGE });
    expect(calls).toBe(3);
  });

  it('retries GET on network errors with backoff', async () => {
    let calls = 0;
    server.use(http.get('/api/alunos', () => {
      calls += 1;
      return calls < 3 ? HttpResponse.error() : HttpResponse.json({ content: [] });
    }));

    await expect(api.get('/alunos')).resolves.toEqual({ content: [] });
    expect(calls).toBe(3);
  });

  it('does not retry 500', async () => {
    let calls = 0;
    server.use(http.get('/api/turmas', () => {
      calls += 1;
      return HttpResponse.json({ status: 500, detail: 'Erro interno' }, { status: 500 });
    }));

    await expect(api.get('/turmas')).rejects.toMatchObject({ status: 500, detail: 'Erro interno' });
    expect(calls).toBe(1);
  });

  it('never resends POST, PUT, PATCH or DELETE on 503', async () => {
    let calls = 0;
    const count = () => {
      calls += 1;
      return unavailable();
    };
    server.use(http.post('/api/matriculas', count), http.put('/api/alunos/1', count),
      http.patch('/api/matriculas/1/status', count), http.delete('/api/alunos/1', count));

    await expect(api.post('/matriculas', { alunoId: 1 })).rejects.toMatchObject({ status: 503, detail: UNAVAILABLE_MESSAGE });
    await expect(api.put('/alunos/1', {})).rejects.toMatchObject({ status: 503 });
    await expect(api.patch('/matriculas/1/status', {})).rejects.toMatchObject({ status: 503 });
    await expect(api.delete('/alunos/1')).rejects.toMatchObject({ status: 503 });
    expect(calls).toBe(4);
  });

  it('never resends POST on network errors', async () => {
    let calls = 0;
    server.use(http.post('/api/matriculas', () => {
      calls += 1;
      return HttpResponse.error();
    }));

    await expect(api.post('/matriculas', { alunoId: 1 })).rejects.toMatchObject({ status: 0, detail: NETWORK_MESSAGE });
    expect(calls).toBe(1);
  });

  it('waits for Retry-After before retrying', async () => {
    const calledAt: number[] = [];
    server.use(http.get('/api/turmas', () => {
      calledAt.push(Date.now());
      return calledAt.length === 1
        ? HttpResponse.json({}, { status: 503, headers: { 'Retry-After': '1' } })
        : HttpResponse.json({});
    }));

    await api.get('/turmas');

    expect(calledAt).toHaveLength(2);
    expect(calledAt[1] - calledAt[0]).toBeGreaterThanOrEqual(900);
  });
});

describe('friendly messages', () => {
  it('explains 429 with the wait time from Retry-After', async () => {
    server.use(http.post('/api/auth/login', () => HttpResponse.json(
      { status: 429, detail: 'Muitas tentativas de login.' }, { status: 429, headers: { 'Retry-After': '30' } })));

    await expect(api.post('/auth/login', {})).rejects.toMatchObject({
      status: 429, detail: 'Muitas tentativas. Aguarde 30 segundos e tente novamente.'
    });
  });

  it('explains 429 without Retry-After', async () => {
    server.use(http.post('/api/auth/login', () => new HttpResponse(null, { status: 429 })));

    await expect(api.post('/auth/login', {})).rejects.toMatchObject({
      status: 429, detail: 'Muitas tentativas. Aguarde um momento e tente novamente.'
    });
  });
});
