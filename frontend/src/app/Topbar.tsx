import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { signOut } from '../features/auth/authApi';
import { getSession } from '../services/session';
import { perfilLabels } from '../types/auth';

export function Topbar() {
  const navigate = useNavigate();
  const usuario = getSession()?.usuario;
  function leave() { signOut(); navigate('/login', { replace: true }); }
  return <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
    <div><p className="text-xs font-semibold uppercase tracking-wide text-teal-700">DistriSchool</p><p className="text-sm text-slate-500">Gestão escolar</p></div>
    <div className="flex items-center gap-3">
      {usuario ? <span className="hidden text-right text-sm text-slate-600 sm:block"><span className="block font-medium text-slate-800">{usuario.nome}</span><span className="block text-xs">{perfilLabels[usuario.perfil]}</span></span> : null}
      <Button variant="ghost" onClick={leave}>Sair</Button>
    </div>
  </header>;
}
