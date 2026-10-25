import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return <main className="grid min-h-screen place-items-center p-6 text-center"><div><h1 className="text-3xl font-bold text-navy">Página não encontrada</h1><Link className="mt-4 inline-block text-teal-700 underline" to="/dashboard">Voltar à visão geral</Link></div></main>;
}
