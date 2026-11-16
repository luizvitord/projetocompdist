import { Navigate, Outlet } from 'react-router-dom';
import { getSession } from '../../services/session';
import { paginaInicial, usaPortal } from './perfis';

/**
 * Guardas por perfil, sempre dentro de RequireAuth. Quem abre uma rota que não é do seu perfil
 * volta para a própria página inicial: ALUNO e RESPONSAVEL para /portal, os demais para /dashboard.
 */
export function RequireCoordenacao() {
  const usuario = getSession()?.usuario;
  if (!usuario) return <Navigate to="/login" replace />;
  return usaPortal(usuario.perfil) ? <Navigate to={paginaInicial(usuario)} replace /> : <Outlet />;
}

/** Administração de contas é exclusiva da coordenação (perfil ADMIN). */
export function RequireAdmin() {
  const usuario = getSession()?.usuario;
  if (!usuario) return <Navigate to="/login" replace />;
  return usuario.perfil === 'ADMIN' ? <Outlet /> : <Navigate to={paginaInicial(usuario)} replace />;
}

export function RequirePortal() {
  const usuario = getSession()?.usuario;
  if (!usuario) return <Navigate to="/login" replace />;
  return usaPortal(usuario.perfil) ? <Outlet /> : <Navigate to={paginaInicial(usuario)} replace />;
}
