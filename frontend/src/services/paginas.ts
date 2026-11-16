import { api } from './http';
import { toPage, type SpringPagedModel } from './pagination';

const TAMANHO_PAGINA = 100;
/** Proteção contra um laço sem fim se a API responder algo inesperado (até 5.000 itens). */
const MAXIMO_PAGINAS = 50;

type Query = Record<string, string | number | boolean | null | undefined | readonly (string | number)[]>;

/**
 * Carrega todas as páginas de uma listagem cujo tamanho é limitado pelo domínio, como os alunos de
 * uma turma (a chamada precisa de todos eles). Não use para listagens que crescem sem limite: nelas,
 * pagine ou busque no servidor.
 */
export async function buscarTodasAsPaginas<T>(path: string, query: Query, signal?: AbortSignal): Promise<T[]> {
  const itens: T[] = [];
  for (let pagina = 0; pagina < MAXIMO_PAGINAS; pagina += 1) {
    const resposta = toPage(await api.get<SpringPagedModel<T>>(path, { query: { ...query, page: pagina, size: TAMANHO_PAGINA }, signal }));
    itens.push(...resposta.content);
    if (pagina + 1 >= resposta.totalPages || resposta.content.length === 0) break;
  }
  return itens;
}
