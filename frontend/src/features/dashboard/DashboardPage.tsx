import { Link } from 'react-router-dom';
import { Card } from '../../components/ui/Card';

const shortcuts = [
  ['Cadastrar aluno', '/alunos'], ['Organizar turmas', '/turmas'], ['Gerenciar matrículas', '/matriculas'], ['Registrar frequência', '/frequencia'], ['Lançar avaliações', '/avaliacoes']
];

export function DashboardPage() {
  return <div className="max-w-5xl"><p className="text-sm font-semibold text-teal-700">Visão geral</p><h1 className="mt-1 text-3xl font-bold text-navy">Rotina da coordenação</h1><p className="mt-2 text-slate-600">Use os atalhos para iniciar os processos escolares. Indicadores gerais serão incluídos quando o backend disponibilizar dados agregados.</p>
    <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{shortcuts.map(([label, to]) => <Link key={to} to={to}><Card className="h-full transition hover:-translate-y-0.5 hover:shadow-md"><h2 className="font-semibold text-slate-800">{label}</h2><p className="mt-2 text-sm text-slate-600">Abrir fluxo</p></Card></Link>)}</div>
  </div>;
}
