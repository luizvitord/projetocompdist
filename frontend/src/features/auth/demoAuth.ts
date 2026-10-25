const DEMO_EMAIL = 'coordenacao@distrischool.com';
const DEMO_PASSWORD = '123456';
const SESSION_KEY = 'distrischool.demo-session';

export function signIn(email: string, password: string): boolean {
  const authenticated = email.trim().toLowerCase() === DEMO_EMAIL && password === DEMO_PASSWORD;
  if (authenticated) sessionStorage.setItem(SESSION_KEY, 'coordenacao');
  return authenticated;
}

export function signOut(): void {
  sessionStorage.removeItem(SESSION_KEY);
}

export function hasDemoSession(): boolean {
  return sessionStorage.getItem(SESSION_KEY) === 'coordenacao';
}
