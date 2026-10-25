import { NavLink } from 'react-router-dom';

const items = [
  ['Visão geral', '/dashboard'], ['Alunos', '/alunos'], ['Turmas', '/turmas'], ['Disciplinas', '/disciplinas'],
  ['Pessoas', '/pessoas'], ['Matrículas', '/matriculas'], ['Avaliações e notas', '/avaliacoes'], ['Frequência', '/frequencia']
];

export function Sidebar() {
  return <aside className="bg-navy p-5 text-slate-100 lg:min-h-screen lg:w-64">
    <p className="text-lg font-bold">DistriSchool</p>
    <p className="mt-1 text-xs text-slate-300">Gestão da coordenação</p>
    <nav aria-label="Navegação principal" className="mt-8 grid gap-1">
      {items.map(([label, to]) => <NavLink key={to} to={to} className={({ isActive }) => `rounded-lg px-3 py-2 text-sm font-medium ${isActive ? 'bg-white/15 text-white' : 'text-slate-300 hover:bg-white/10 hover:text-white'}`}>{label}</NavLink>)}
    </nav>
  </aside>;
}
