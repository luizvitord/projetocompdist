import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { signOut } from '../features/auth/demoAuth';

export function Topbar() {
  const navigate = useNavigate();
  function leave() { signOut(); navigate('/login', { replace: true }); }
  return <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
    <div><p className="text-xs font-semibold uppercase tracking-wide text-teal-700">DistriSchool</p><p className="text-sm text-slate-500">Coordenação escolar</p></div>
    <div className="flex items-center gap-3"><span className="hidden text-sm text-slate-600 sm:block">Coordenação</span><Button variant="ghost" onClick={leave}>Sair</Button></div>
  </header>;
}
