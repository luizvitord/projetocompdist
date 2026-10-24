import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppShell } from './AppShell';
import { NotFoundPage } from './NotFoundPage';
import { RequireDemoSession } from '../features/auth/RequireDemoSession';
import { LoginPage } from '../features/auth/LoginPage';
import { DashboardPage } from '../features/dashboard/DashboardPage';

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { element: <RequireDemoSession />, children: [{ element: <AppShell />, children: [
    { path: '/', element: <Navigate to="/dashboard" replace /> },
    { path: '/dashboard', element: <DashboardPage /> }
  ] }] },
  { path: '*', element: <NotFoundPage /> }
]);
