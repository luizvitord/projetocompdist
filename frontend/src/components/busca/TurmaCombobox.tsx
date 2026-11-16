import { useId, useState } from 'react';
import { SearchCombobox } from '../ui/SearchCombobox';
import { LIMITE_SUGESTOES } from './AlunoCombobox';
import { api } from '../../services/http';
import { toPage, type SpringPagedModel } from '../../services/pagination';
import type { Turma } from '../../types/turma';

const ANO_MINIMO = 2000;
const ANO_MAXIMO = 2100;

export const descricaoTurma = (turma: Turma) => `${turma.codigo} — ${turma.nome} — ${turma.anoLetivo} — ${turma.turno}`;

type Props = {
  name?: string;
  label?: string;
  required?: boolean;
  disabled?: boolean;
  /** Turma que não deve aparecer nas sugestões (ex.: a turma atual numa transferência). */
  excluirId?: number;
  /** Ano letivo inicial; o padrão é o ano atual. */
  anoInicial?: number;
  /** Ano letivo fixo, sem opção de trocar (ex.: transferência, que exige o mesmo ano). */
  anoFixo?: number;
  defaultSelected?: Turma | null;
  onSelect?: (turma: Turma | null) => void;
};

/** Campo de turma: filtra pelo ano letivo e por parte do código ou do nome; envia o id da turma. */
export function TurmaCombobox({ name = 'turmaId', label = 'Turma', required, disabled, excluirId, anoInicial, anoFixo, defaultSelected = null, onSelect }: Props) {
  const anoId = useId();
  const [anoTexto, setAnoTexto] = useState(String(anoFixo ?? anoInicial ?? defaultSelected?.anoLetivo ?? new Date().getFullYear()));
  const ano = Number(anoTexto);
  const anoValido = /^\d{4}$/.test(anoTexto) && ano >= ANO_MINIMO && ano <= ANO_MAXIMO;

  function trocarAno(valor: string) {
    setAnoTexto(valor);
    // A turma escolhida pertence ao ano anterior; o campo é recriado (key) e começa vazio
    onSelect?.(null);
  }
  function buscar(texto: string, signal: AbortSignal) {
    return api.get<SpringPagedModel<Turma>>('/turmas', { query: { anoLetivo: ano, busca: texto, page: 0, size: LIMITE_SUGESTOES }, signal })
      .then((data) => toPage(data).content.filter((turma) => turma.id !== excluirId));
  }

  return <div className="grid gap-2 sm:grid-cols-[8rem_1fr]">
    {anoFixo !== undefined
      ? <div><span>Ano letivo</span><p className="mt-1 rounded border bg-slate-50 p-2 text-slate-700">{anoFixo}</p></div>
      : <div><label htmlFor={anoId}>Ano letivo</label><input id={anoId} type="number" inputMode="numeric" min={ANO_MINIMO} max={ANO_MAXIMO} value={anoTexto} disabled={disabled} onChange={(event) => trocarAno(event.target.value)} className="mt-1 w-full rounded border p-2" /></div>}
    <SearchCombobox<Turma>
      key={anoValido ? ano : 'ano-invalido'}
      label={label}
      name={name}
      required={required}
      disabled={disabled || !anoValido}
      minLength={0}
      placeholder={anoValido ? 'Código ou nome da turma' : 'Informe um ano letivo válido'}
      emptyMessage={`Nenhuma turma encontrada em ${anoValido ? ano : 'este ano letivo'}.`}
      defaultSelected={defaultSelected && (!anoValido || defaultSelected.anoLetivo === ano) ? defaultSelected : null}
      search={buscar}
      getKey={(turma) => turma.id}
      getLabel={descricaoTurma}
      onSelect={onSelect}
    />
  </div>;
}
