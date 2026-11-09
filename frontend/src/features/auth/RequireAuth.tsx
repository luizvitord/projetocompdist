import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { getSession } from '../../services/session';

export function RequireAuth() {
  const location = useLocation();
  return getSession() ? <Outlet /> : <Navigate to="/login" replace state={{ from: location.pathname }} />;
}
