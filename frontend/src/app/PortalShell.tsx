import { Outlet } from 'react-router-dom';
import { Topbar } from './Topbar';

/** Layout do portal do aluno e do responsável: só o topo (notificações e sair), sem a Sidebar. */
export function PortalShell() {
  return <div className="min-h-screen bg-slate-50"><Topbar subtitulo="Portal do aluno" /><main className="mx-auto max-w-5xl p-5 sm:p-8"><Outlet /></main></div>;
}
