export type ApiProblem = Error & {
  status: number;
  detail: string;
  fieldErrors: Record<string, string>;
};

type RawProblem = {
  status?: number;
  detail?: string;
  title?: string;
  erros?: Record<string, string>;
  errors?: Record<string, string>;
};

export function toApiProblem(payload: RawProblem, fallbackStatus = 500): ApiProblem {
  const detail = payload.detail ?? payload.title ?? 'Não foi possível concluir a operação.';
  const problem = new Error(detail) as ApiProblem;
  problem.name = 'ApiProblem';
  problem.status = payload.status ?? fallbackStatus;
  problem.detail = detail;
  problem.fieldErrors = payload.erros ?? payload.errors ?? {};
  return problem;
}
