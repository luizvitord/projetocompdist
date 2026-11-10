import type { Sessao } from '../types/auth';

// sessionStorage: a sessão termina quando a aba é fechada
const SESSION_KEY = 'distrischool.session';

export function saveSession(session: Sessao): void {
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  sessionStorage.removeItem(SESSION_KEY);
}

/** Sessão atual, ou null se não houver login ou se o token já expirou. */
export function getSession(): Sessao | null {
  const raw = sessionStorage.getItem(SESSION_KEY);
  if (!raw) return null;
  try {
    const session = JSON.parse(raw) as Sessao;
    if (new Date(session.expiraEm).getTime() <= Date.now()) {
      clearSession();
      return null;
    }
    return session;
  } catch {
    clearSession();
    return null;
  }
}
