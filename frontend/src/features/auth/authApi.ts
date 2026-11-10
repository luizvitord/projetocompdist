import { api } from '../../services/http';
import { clearSession, saveSession } from '../../services/session';
import type { Sessao } from '../../types/auth';

/** Faz login no usuarios-service (via gateway) e guarda o token para as próximas requisições. */
export async function signIn(email: string, senha: string): Promise<Sessao> {
  const session = await api.post<Sessao>('/auth/login', { email: email.trim(), senha });
  saveSession(session);
  return session;
}

export function signOut(): void {
  clearSession();
}
