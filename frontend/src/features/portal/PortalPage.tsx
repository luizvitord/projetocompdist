import { useEffect, useMemo, useState } from 'react';
import { BoletimPanel } from '../alunos/BoletimPanel';
import { FrequenciaAlunoPanel } from '../alunos/FrequenciaAlunoPanel';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { api } from '../../services/http';
import type { SpringPagedModel } from '../../services/pagination';
import { getSession } from '../../services/session';
import type { Aluno } from '../../types/aluno';
import type { Matricula } from '../../types/escolar';

type Dados = { aluno: Aluno; anos: number[] };

/** Anos letivos das matrículas, do mais recente para o mais antigo, sem repetir. */
function anosLetivos(matriculas: Matricula[]): number[] {
  return [...new Set(matriculas.map((matricula) => matricula.anoLetivo))].sort((a, b) => b - a);
}

/** Ano inicial do seletor: o ano corrente, se o aluno tiver matrícula nele; senão, o mais recente. */
function anoInicial(anos: number[]): number | null {
  const atual = new Date().getFullYear();
  return anos.includes(atual) ? atual : anos[0] ?? null;
}

/**
 * Portal do aluno e do responsável ("Portal para pais"). Mostra só o aluno ligado à conta
 * (referenciaId do login): dados, boletim e frequência do ano letivo escolhido. O gateway recusa
 * qualquer outro aluno; as notificações ficam no sino do topo.
 */
export function PortalPage() {
  const usuario = getSession()?.usuario;
  const alunoId = usuario?.referenciaId ?? null;
  const [dados, setDados] = useState<Dados | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  const [ano, setAno] = useState<number | null>(null);

  useEffect(() => {
    if (alunoId === null) return;
    setState('loading');
    Promise.all([
      api.get<Aluno>(`/alunos/${alunoId}`),
      api.get<SpringPagedModel<Matricula>>('/matriculas', { query: { alunoId, size: 100 } })
    ]).then(([aluno, matriculas]) => {
      const anos = anosLetivos(matriculas.content ?? []);
      setDados({ aluno, anos });
      setAno(anoInicial(anos));
      setState('ready');
    }).catch(() => setState('error'));
  }, [alunoId, attempt]);

  const textos = useMemo(() => {
    const nome = dados?.aluno.nome ?? '';
    return usuario?.perfil === 'ALUNO'
      ? { titulo: 'Meu portal', boletim: 'Meu boletim', frequencia: 'Minha frequência' }
      : { titulo: `Acompanhamento de ${nome}`, boletim: `Boletim de ${nome}`, frequencia: `Frequência de ${nome}` };
  }, [dados, usuario?.perfil]);

  if (alunoId === null) {
    return <div role="alert" className="rounded-lg bg-amber-50 p-5 text-amber-900">
      <h1 className="text-lg font-bold">Conta ainda não ligada a um aluno</h1>
      <p className="mt-2">Sua conta não está associada a nenhum aluno, então não há boletim nem frequência para mostrar. Procure a coordenação da escola para concluir o cadastro.</p>
    </div>;
  }

  return <section className="space-y-5">
    {state === 'loading' ? <p role="status">Carregando dados do aluno...</p> : null}
    {state === 'error' ? <div role="alert" className="rounded-lg bg-rose-50 p-4 text-rose-800"><p>Não foi possível carregar os dados do aluno.</p><Button className="mt-3" type="button" variant="secondary" onClick={() => setAttempt((value) => value + 1)}>Tentar novamente</Button></div> : null}
    {state === 'ready' && dados ? <>
      <header>
        <h1 className="text-2xl font-bold text-navy">{textos.titulo}</h1>
        <p className="text-slate-600">{dados.aluno.nome} · Matrícula {dados.aluno.matricula}</p>
      </header>
      <Card>
        <h2 className="font-bold text-navy">Dados do aluno</h2>
        <dl className="mt-3 grid gap-3 sm:grid-cols-2">
          <div><dt className="text-xs uppercase text-slate-500">Nome</dt><dd>{dados.aluno.nome}</dd></div>
          <div><dt className="text-xs uppercase text-slate-500">Matrícula</dt><dd>{dados.aluno.matricula}</dd></div>
          <div><dt className="text-xs uppercase text-slate-500">E-mail</dt><dd>{dados.aluno.email}</dd></div>
          <div><dt className="text-xs uppercase text-slate-500">Telefone</dt><dd>{dados.aluno.telefone}</dd></div>
          <div><dt className="text-xs uppercase text-slate-500">Nascimento</dt><dd>{dados.aluno.dataNascimento}</dd></div>
        </dl>
      </Card>
      {ano === null
        ? <p className="rounded-lg bg-slate-100 p-4 text-slate-700">Nenhuma matrícula encontrada. O boletim e a frequência aparecem aqui depois da primeira matrícula.</p>
        : <>
          <label className="block w-fit text-sm font-medium text-slate-700">Ano letivo
            <select className="mt-1 block rounded-lg border border-slate-300 bg-white px-3 py-2" value={ano} onChange={(event) => setAno(Number(event.target.value))}>
              {dados.anos.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
          <div className="grid gap-5 lg:grid-cols-2">
            <Card><BoletimPanel alunoId={String(alunoId)} anoLetivo={ano} titulo={`${textos.boletim} (${ano})`} /></Card>
            <Card><FrequenciaAlunoPanel alunoId={String(alunoId)} anoLetivo={ano} titulo={`${textos.frequencia} (${ano})`} /></Card>
          </div>
        </>}
    </> : null}
  </section>;
}
