import { ApiProblem, toApiProblem } from './problemDetail';
import { clearSession, getSession } from './session';

type QueryValue = string | number | boolean | null | undefined;
type RequestOptions = {
  query?: Record<string, QueryValue>;
  signal?: AbortSignal;
  /** Tempo máximo de cada tentativa, em milissegundos. */
  timeoutMs?: number;
};

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';

const REQUEST_TIMEOUT_MS = 15_000;
/** Novas tentativas, além da primeira, só para GET. */
const MAX_RETRIES = 2;
const RETRY_BASE_DELAY_MS = 300;
const MAX_RETRY_AFTER_MS = 10_000;
// Falhas temporárias do gateway ou do serviço; 500 não é repetido (pode ser um erro de verdade)
const RETRYABLE_STATUS = new Set([502, 503, 504]);

export const UNAVAILABLE_MESSAGE = 'Serviço temporariamente indisponível. Tente novamente em instantes.';
export const TIMEOUT_MESSAGE = 'O servidor demorou demais para responder. Tente novamente em instantes.';
export const NETWORK_MESSAGE = 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';

function pathWithQuery(path: string, query?: Record<string, QueryValue>) {
  const url = new URL(`${baseUrl}${path}`, window.location.origin);
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  });
  return `${url.pathname}${url.search}`;
}

function requestHeaders(body: unknown): Record<string, string> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = getSession()?.token;
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

/** Erro sem resposta HTTP (rede ou timeout): status 0, no mesmo formato de ApiProblem. */
function problemWithoutResponse(detail: string): ApiProblem {
  return toApiProblem({ detail }, 0);
}

function tooManyRequestsMessage(retryAfterMs: number | undefined) {
  if (retryAfterMs === undefined) return 'Muitas tentativas. Aguarde um momento e tente novamente.';
  const seconds = Math.max(1, Math.ceil(retryAfterMs / 1000));
  return `Muitas tentativas. Aguarde ${seconds} ${seconds === 1 ? 'segundo' : 'segundos'} e tente novamente.`;
}

/** Retry-After em segundos ou como data HTTP; undefined se ausente ou inválido. */
function retryAfterMs(response: Response): number | undefined {
  const value = response.headers.get('Retry-After');
  if (value === null || value.trim() === '') return undefined;
  const seconds = Number(value);
  if (Number.isFinite(seconds)) return Math.max(0, seconds * 1000);
  const date = Date.parse(value);
  return Number.isNaN(date) ? undefined : Math.max(0, date - Date.now());
}

/** Espera exponencial com jitter (metade fixa, metade aleatória), ou o Retry-After do servidor. */
function retryDelayMs(attempt: number, response?: Response) {
  const retryAfter = response ? retryAfterMs(response) : undefined;
  if (retryAfter !== undefined) return Math.min(retryAfter, MAX_RETRY_AFTER_MS);
  const exponential = RETRY_BASE_DELAY_MS * 2 ** attempt;
  return exponential / 2 + Math.random() * (exponential / 2);
}

/** Espera interrompida na hora se a chamada for cancelada. */
function wait(ms: number, signal?: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(signal.reason);
      return;
    }
    const onAbort = () => {
      clearTimeout(timer);
      reject(signal?.reason);
    };
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

const NETWORK_ERROR = Symbol('network-error');

/**
 * Uma tentativa, com timeout próprio combinado ao signal de quem chamou. Cancelamento pelo chamador
 * propaga o erro original (AbortError), como antes; timeout vira ApiProblem e não é repetido (o
 * servidor pode estar processando a requisição).
 */
async function attempt(method: string, url: string, body: unknown, options?: RequestOptions) {
  const timeout = AbortSignal.timeout(options?.timeoutMs ?? REQUEST_TIMEOUT_MS);
  const signal = options?.signal ? AbortSignal.any([options.signal, timeout]) : timeout;
  try {
    return await fetch(url, {
      method,
      signal,
      headers: requestHeaders(body),
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  } catch (error) {
    if (options?.signal?.aborted) throw error;
    if (timeout.aborted) throw problemWithoutResponse(TIMEOUT_MESSAGE);
    // fetch rejeita com TypeError quando não há resposta (servidor inacessível, rede caída)
    if (error instanceof TypeError) return NETWORK_ERROR;
    throw error;
  }
}

async function request<T>(method: string, path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  const url = pathWithQuery(path, options?.query);
  // POST, PUT, PATCH e DELETE nunca são reenviados: a primeira tentativa pode ter sido processada
  const retries = method === 'GET' ? MAX_RETRIES : 0;

  let response: Response;
  for (let tries = 0; ; tries += 1) {
    const result = await attempt(method, url, body, options);
    const canRetry = tries < retries;
    if (result === NETWORK_ERROR) {
      if (!canRetry) throw problemWithoutResponse(NETWORK_MESSAGE);
      await wait(retryDelayMs(tries), options?.signal);
      continue;
    }
    response = result;
    if (canRetry && RETRYABLE_STATUS.has(response.status)) {
      // Libera a conexão da resposta descartada; não precisa esperar
      response.body?.cancel().catch(() => undefined);
      await wait(retryDelayMs(tries, response), options?.signal);
      continue;
    }
    break;
  }

  // Token ausente, inválido ou expirado: o gateway responde 401 e é preciso entrar de novo.
  // No próprio login, 401 significa só senha errada e a tela mostra o erro.
  if (response.status === 401 && path !== '/auth/login') {
    clearSession();
    window.location.assign('/login');
  }
  if (!response.ok) {
    let payload: Record<string, unknown> = {};
    try { payload = await response.json() as Record<string, unknown>; } catch { /* empty/non-JSON response */ }
    // Mensagens amigáveis para indisponibilidade e excesso de tentativas, no lugar do detalhe técnico
    if (response.status === 503) payload = { ...payload, detail: UNAVAILABLE_MESSAGE };
    if (response.status === 429) payload = { ...payload, detail: tooManyRequestsMessage(retryAfterMs(response)) };
    throw toApiProblem(payload, response.status);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => request<T>('GET', path, undefined, options),
  post: <T>(path: string, body: unknown, options?: RequestOptions) => request<T>('POST', path, body, options),
  put: <T>(path: string, body: unknown, options?: RequestOptions) => request<T>('PUT', path, body, options),
  patch: <T>(path: string, body: unknown, options?: RequestOptions) => request<T>('PATCH', path, body, options),
  delete: <T>(path: string, options?: RequestOptions) => request<T>('DELETE', path, undefined, options)
};

export function isApiProblem(error: unknown): error is ApiProblem {
  return error instanceof Error && error.name === 'ApiProblem';
}
