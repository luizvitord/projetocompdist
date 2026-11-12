import { api } from '../../services/http';
import { toPage, type Page, type SpringPagedModel } from '../../services/pagination';
import type { Notificacao } from '../../types/notificacao';

/** O destinatário é sempre o usuário do token; a API não aceita outro por parâmetro. */
export async function listarNotificacoes(size = 10, signal?: AbortSignal): Promise<Page<Notificacao>> {
  return toPage(await api.get<SpringPagedModel<Notificacao>>('/notificacoes', { query: { size }, signal }));
}

export async function contarNaoLidas(signal?: AbortSignal): Promise<number> {
  return (await api.get<{ total: number }>('/notificacoes/nao-lidas/contagem', { signal })).total;
}

export function marcarComoLida(id: number): Promise<Notificacao> {
  return api.patch<Notificacao>(`/notificacoes/${id}/lida`, undefined);
}

export async function marcarTodasComoLidas(): Promise<number> {
  return (await api.post<{ atualizadas: number }>('/notificacoes/lidas', undefined)).atualizadas;
}
