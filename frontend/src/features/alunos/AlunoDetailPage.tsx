import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { api } from '../../services/http';
import type { Aluno } from '../../types/aluno';
import { BoletimPanel } from './BoletimPanel';
import { FrequenciaAlunoPanel } from './FrequenciaAlunoPanel';

export function AlunoDetailPage() {
  const { id } = useParams();
  const [aluno, setAluno] = useState<Aluno | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    setState('loading');
    api.get<Aluno>(`/alunos/${id}`).then((response) => { setAluno(response); setState('ready'); }).catch(() => setState('error'));
  }, [attempt, id]);

  return <section className="space-y-5"><Link className="text-sm font-semibold text-teal-700" to="/alunos">← Voltar para alunos</Link>
    {state === 'loading' ? <p role="status">Carregando aluno...</p> : null}
    {state === 'error' ? <div role="alert" className="rounded-lg bg-rose-50 p-4 text-rose-800"><p>Não foi possível carregar os dados do aluno.</p><Button className="mt-3" type="button" variant="secondary" onClick={() => setAttempt((value) => value + 1)}>Tentar novamente</Button></div> : null}
    {state === 'ready' && aluno ? <><header><h1 className="text-2xl font-bold text-navy">{aluno.nome}</h1><p className="text-slate-600">Matrícula {aluno.matricula}</p></header><Card><h2 className="font-bold text-navy">Dados cadastrais</h2><dl className="mt-3 grid gap-3 sm:grid-cols-2"><div><dt className="text-xs uppercase text-slate-500">E-mail</dt><dd>{aluno.email}</dd></div><div><dt className="text-xs uppercase text-slate-500">Telefone</dt><dd>{aluno.telefone}</dd></div><div><dt className="text-xs uppercase text-slate-500">Nascimento</dt><dd>{aluno.dataNascimento}</dd></div><div><dt className="text-xs uppercase text-slate-500">Endereço</dt><dd>{aluno.endereco}</dd></div></dl></Card><div className="grid gap-5 lg:grid-cols-2"><Card><BoletimPanel alunoId={id ?? ''} anoLetivo={new Date().getFullYear()} /></Card><Card><FrequenciaAlunoPanel alunoId={id ?? ''} anoLetivo={new Date().getFullYear()} /></Card></div></> : null}
  </section>;
}
