import { useCallback, useEffect, useState } from 'react';
import { Button } from '../../components/ui/Button';
import { api } from '../../services/http';

type Boletim = { situacao: string; disciplinas: { nome: string; mediaAnual: number; situacao: string }[] };

export function BoletimPanel({ alunoId, anoLetivo }: { alunoId: string; anoLetivo: number }) {
  const [data, setData] = useState<Boletim | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const load = useCallback(() => {
    setState('loading');
    api.get<Boletim>(`/alunos/${alunoId}/boletim`, { query: { anoLetivo } }).then((response) => { setData(response); setState('ready'); }).catch(() => setState('error'));
  }, [alunoId, anoLetivo]);
  useEffect(() => { load(); }, [load]);
  return <section><h2 className="font-bold text-navy">Boletim {anoLetivo}</h2>{state === 'loading' ? <p className="mt-2 text-sm text-slate-600">Carregando boletim...</p> : null}{state === 'error' ? <div className="mt-2 text-sm text-slate-600"><p>Não foi possível carregar o boletim agora.</p><Button className="mt-2" variant="secondary" type="button" onClick={load}>Tentar novamente</Button></div> : null}{state === 'ready' && data ? <><p className="mt-2">Situação anual: {data.situacao}</p><ul className="mt-2">{data.disciplinas.map((item) => <li key={item.nome}>{item.nome}: média {item.mediaAnual} — {item.situacao}</li>)}</ul></> : null}</section>;
}
