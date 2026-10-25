import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { hasDemoSession } from './demoAuth';

export function RequireDemoSession() {
  const location = useLocation();
  return hasDemoSession() ? <Outlet /> : <Navigate to="/login" replace state={{ from: location.pathname }} />;
}
