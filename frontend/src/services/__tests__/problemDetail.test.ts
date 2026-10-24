import { describe, expect, it } from 'vitest';
import { toApiProblem } from '../problemDetail';

describe('toApiProblem', () => {
  it('maps the backend erros map into fieldErrors', () => {
    const problem = toApiProblem({ status: 400, detail: 'Dados inválidos', erros: { nome: 'não pode ficar em branco' } });

    expect(problem.status).toBe(400);
    expect(problem.fieldErrors).toEqual({ nome: 'não pode ficar em branco' });
  });
});
