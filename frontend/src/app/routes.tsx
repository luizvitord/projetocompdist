import { Navigate, type RouteObject } from 'react-router-dom';
import { AppShell } from './AppShell';
import { PortalShell } from './PortalShell';
import { NotFoundPage } from './NotFoundPage';
import { RequireAuth } from '../features/auth/RequireAuth';
import { RequireAdmin, RequireCoordenacao, RequirePortal } from '../features/auth/RequirePerfil';
import { LoginPage } from '../features/auth/LoginPage';
import { PortalPage } from '../features/portal/PortalPage';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { AlunosPage } from '../features/alunos/AlunosPage';
import { AlunoDetailPage } from '../features/alunos/AlunoDetailPage';
import { TurmasPage } from '../features/turmas/TurmasPage';
import { TurmaDetailPage } from '../features/turmas/TurmaDetailPage';
import { DisciplinasPage } from '../features/disciplinas/DisciplinasPage';
import { PessoasPage } from '../features/pessoas/PessoasPage';
import { MatriculasPage } from '../features/matriculas/MatriculasPage';
import { AvaliacoesPage } from '../features/avaliacoes/AvaliacoesPage';
import { FrequenciaPage } from '../features/frequencia/FrequenciaPage';
import { UsuariosPage } from '../features/usuarios/UsuariosPage';

/** Rotas da coordenação (ADMIN e PROFESSOR). ALUNO e RESPONSAVEL são desviados para /portal. */
const coordenacaoRoutes: RouteObject[] = [
  { path: '/', element: <Navigate to="/dashboard" replace /> },
  { path: '/dashboard', element: <DashboardPage /> },
  { path: '/alunos', element: <AlunosPage /> },
  { path: '/alunos/:id', element: <AlunoDetailPage /> },
  { path: '/turmas', element: <TurmasPage /> },
  { path: '/turmas/:id', element: <TurmaDetailPage /> },
  { path: '/disciplinas', element: <DisciplinasPage /> },
  { path: '/pessoas', element: <PessoasPage /> },
  { path: '/matriculas', element: <MatriculasPage /> },
  { path: '/avaliacoes', element: <AvaliacoesPage /> },
  { path: '/frequencia', element: <FrequenciaPage /> },
  { element: <RequireAdmin />, children: [{ path: '/usuarios', element: <UsuariosPage /> }] }
];

/** Árvore de rotas do portal; os testes a montam num MemoryRouter. */
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { element: <RequireAuth />, children: [
    { element: <RequirePortal />, children: [{ element: <PortalShell />, children: [{ path: '/portal', element: <PortalPage /> }] }] },
    { element: <RequireCoordenacao />, children: [{ element: <AppShell />, children: coordenacaoRoutes }] }
  ] },
  { path: '*', element: <NotFoundPage /> }
];
