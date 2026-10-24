import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppShell } from './AppShell';
import { NotFoundPage } from './NotFoundPage';
import { RequireDemoSession } from '../features/auth/RequireDemoSession';
import { LoginPage } from '../features/auth/LoginPage';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { AlunosPage } from '../features/alunos/AlunosPage';
import { AlunoDetailPage } from '../features/alunos/AlunoDetailPage';
import { TurmasPage } from '../features/turmas/TurmasPage';
import { TurmaDetailPage } from '../features/turmas/TurmaDetailPage';
import { DisciplinasPage } from '../features/disciplinas/DisciplinasPage';
import { PessoasPage } from '../features/pessoas/PessoasPage';
import { MatriculasPage } from '../features/matriculas/MatriculasPage';

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { element: <RequireDemoSession />, children: [{ element: <AppShell />, children: [
    { path: '/', element: <Navigate to="/dashboard" replace /> },
    { path: '/dashboard', element: <DashboardPage /> },
    { path: '/alunos', element: <AlunosPage /> },
    { path: '/alunos/:id', element: <AlunoDetailPage /> },
    { path: '/turmas', element: <TurmasPage /> },
    { path: '/turmas/:id', element: <TurmaDetailPage /> },
    { path: '/disciplinas', element: <DisciplinasPage /> },
    { path: '/pessoas', element: <PessoasPage /> },
    { path: '/matriculas', element: <MatriculasPage /> }
  ] }] },
  { path: '*', element: <NotFoundPage /> }
]);
