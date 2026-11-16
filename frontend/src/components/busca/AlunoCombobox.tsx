import { SearchCombobox } from '../ui/SearchCombobox';
import { api } from '../../services/http';
import { toPage, type SpringPagedModel } from '../../services/pagination';
import type { Aluno } from '../../types/aluno';

export const LIMITE_SUGESTOES = 10;
const SO_DIGITOS = /^\d+$/;

/** Busca por parte do nome ou, se o texto tiver só dígitos, pelo número de matrícula completo. */
export function buscarAlunos(texto: string, signal: AbortSignal) {
  const filtro = SO_DIGITOS.test(texto) ? { matricula: texto } : { nome: texto };
  return api.get<SpringPagedModel<Aluno>>('/alunos', { query: { ...filtro, page: 0, size: LIMITE_SUGESTOES }, signal })
    .then((data) => toPage(data).content);
}

type Props = { name?: string; label?: string; required?: boolean; disabled?: boolean; onSelect?: (aluno: Aluno | null) => void };

/** Campo de aluno: envia o número de matrícula, como os endpoints de matrícula esperam. */
export function AlunoCombobox({ name = 'matriculaAluno', label = 'Aluno', required, disabled, onSelect }: Props) {
  return <SearchCombobox<Aluno>
    label={label}
    name={name}
    required={required}
    disabled={disabled}
    minLength={2}
    placeholder="Nome ou número de matrícula"
    hint="Digite parte do nome ou o número de matrícula completo."
    emptyMessage="Nenhum aluno encontrado."
    search={buscarAlunos}
    getKey={(aluno) => aluno.id}
    getLabel={(aluno) => `${aluno.nome} — ${aluno.matricula}`}
    getValue={(aluno) => aluno.matricula}
    onSelect={onSelect}
  />;
}
