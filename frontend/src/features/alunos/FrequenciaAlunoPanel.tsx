import { useCallback, useEffect, useState } from 'react';
import { Button } from '../../components/ui/Button';
import { api } from '../../services/http';

type Frequencia = { geral: { percentual: number; abaixoDoMinimo: boolean } };

export function FrequenciaAlunoPanel({ alunoId, anoLetivo }: { alunoId: string; anoLetivo: number }) {
  const [data, setData] = useState<Frequencia | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const load = useCallback(() => {
    setState('loading');
    api.get<Frequencia>(`/frequencias/alunos/${alunoId}`, { query: { anoLetivo } }).then((response) => { setData(response); setState('ready'); }).catch(() => setState('error'));
  }, [alunoId, anoLetivo]);
  useEffect(() => { load(); }, [load]);
  return <section><h2 className="font-bold text-navy">Frequência anual</h2>{state === 'loading' ? <p className="mt-2 text-sm text-slate-600">Carregando frequência...</p> : null}{state === 'error' ? <div className="mt-2 text-sm text-slate-600"><p>Não foi possível carregar a frequência agora.</p><Button className="mt-2" variant="secondary" type="button" onClick={load}>Tentar novamente</Button></div> : null}{state === 'ready' && data ? <p className="mt-2">{data.geral.percentual}% — {data.geral.abaixoDoMinimo ? 'Abaixo da frequência mínima' : 'Frequência adequada'}</p> : null}</section>;
}
