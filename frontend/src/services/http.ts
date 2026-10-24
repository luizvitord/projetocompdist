import { ApiProblem, toApiProblem } from './problemDetail';

type QueryValue = string | number | boolean | null | undefined;
type RequestOptions = { query?: Record<string, QueryValue>; signal?: AbortSignal };

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';

function pathWithQuery(path: string, query?: Record<string, QueryValue>) {
  const url = new URL(`${baseUrl}${path}`, window.location.origin);
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  });
  return `${url.pathname}${url.search}`;
}

async function request<T>(method: string, path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  const response = await fetch(pathWithQuery(path, options?.query), {
    method,
    signal: options?.signal,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  if (!response.ok) {
    let payload: Record<string, unknown> = {};
    try { payload = await response.json() as Record<string, unknown>; } catch { /* empty/non-JSON response */ }
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
