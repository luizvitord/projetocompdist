import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Button } from '../../components/ui/Button';
import { DataTable, type DataTableColumn } from '../../components/ui/DataTable';
import { Dialog } from '../../components/ui/Dialog';
import { EmptyState } from '../../components/ui/EmptyState';
import { Pagination } from '../../components/ui/Pagination';
import { api, isApiProblem } from '../../services/http';
import { toPage, type Page, type SpringPagedModel } from '../../services/pagination';

export type FieldOption = { value: string; label: string };
export type Field = { name: string; label: string; type?: 'text' | 'email' | 'number' | 'date' | 'checkbox' | 'select' | 'cpf'; required?: boolean; defaultValue?: string | boolean; options?: FieldOption[] };
type Resource = { id: number; [key: string]: unknown };
type Filters = Record<string, string | number | boolean>;
const EMPTY_FILTERS: Filters = {};

function isValidCpf(value: string) {
  const digits = value.replace(/\D/g, '');
  if (digits.length !== 11 || /^(\d)\1+$/.test(digits)) return false;
  const calculateDigit = (length: number) => {
    let sum = 0;
    for (let index = 0; index < length; index += 1) sum += Number(digits[index]) * (length + 1 - index);
    const remainder = (sum * 10) % 11;
    return remainder === 10 ? 0 : remainder;
  };
  return calculateDigit(9) === Number(digits[9]) && calculateDigit(10) === Number(digits[10]);
}

export function RegistryPage<T extends Resource>({ title, singular, path, columns, fields, filters = EMPTY_FILTERS, toPayload }: {
  title: string; singular: string; path: string; columns: DataTableColumn<T>[]; fields: Field[]; filters?: Filters; toPayload?: (form: FormData) => Record<string, unknown>;
}) {
  const [pageNumber, setPageNumber] = useState(0);
  const [page, setPage] = useState<Page<T> | null>(null);
  const [error, setError] = useState('');
  const [formError, setFormError] = useState<Record<string, string>>({});
  const [refresh, setRefresh] = useState(0);
  const filtersKey = JSON.stringify(filters);
  const stableFilters = useMemo(() => JSON.parse(filtersKey) as Filters, [filtersKey]);

  useEffect(() => {
    api.get<SpringPagedModel<T>>(path, { query: { ...stableFilters, page: pageNumber, size: 20 } })
      .then((response) => { setPage(toPage(response)); setError(''); })
      .catch(() => setError(`Não foi possível carregar ${title.toLowerCase()}.`));
  }, [pageNumber, path, refresh, stableFilters, title]);

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const cpf = fields.find((field) => field.type === 'cpf');
    if (cpf && !isValidCpf(String(form.get(cpf.name) ?? ''))) {
      setFormError({ [cpf.name]: 'Informe um CPF válido.' });
      return;
    }
    const payload = toPayload ? toPayload(form) : Object.fromEntries(form.entries());
    try {
      await api.post<T>(path, payload);
      setFormError({});
      formElement.reset();
      setRefresh((value) => value + 1);
    } catch (reason) {
      setFormError(isApiProblem(reason) ? reason.fieldErrors : { form: 'Não foi possível salvar os dados.' });
    }
  }

  return <section className="space-y-5">
    <header className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-bold text-navy">{title}</h1><p className="mt-1 text-sm text-slate-600">Cadastre e acompanhe {title.toLowerCase()}.</p></div>
      <Dialog title={`Novo ${singular}`} trigger={<Button>Novo {singular}</Button>}><form className="grid gap-3 sm:grid-cols-2" onSubmit={create}>
        {fields.map((field) => <label key={field.name} className="text-sm font-medium text-slate-700">{field.label}
          {field.type === 'checkbox' ? <input className="ml-2" name={field.name} type="checkbox" defaultChecked={Boolean(field.defaultValue)} /> : field.type === 'select' ? <select className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" name={field.name} required={field.required} defaultValue={typeof field.defaultValue === 'string' ? field.defaultValue : ''}><option value="">Selecione</option>{field.options?.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select> : <input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" name={field.name} type={field.type === 'cpf' ? 'text' : field.type ?? 'text'} inputMode={field.type === 'cpf' ? 'numeric' : undefined} placeholder={field.type === 'cpf' ? '000.000.000-00' : undefined} maxLength={field.type === 'cpf' ? 14 : undefined} required={field.required} defaultValue={typeof field.defaultValue === 'string' ? field.defaultValue : undefined} />}
          {formError[field.name] ? <span className="mt-1 block text-xs text-rose-700">{formError[field.name]}</span> : null}
        </label>)}
        {formError.form ? <p role="alert" className="text-sm text-rose-700 sm:col-span-2">{formError.form}</p> : null}
        <Button className="sm:col-span-2" type="submit">Salvar</Button>
      </form></Dialog>
    </header>
    {error ? <p role="alert" className="rounded-lg bg-rose-50 p-4 text-rose-800">{error}</p> : null}
    {page && page.content.length > 0 ? <><DataTable columns={columns} items={page.content} keyFor={(item) => item.id} /><Pagination page={page.number} totalPages={page.totalPages} onPageChange={setPageNumber} /></> : <EmptyState title={`Nenhum ${singular.toLowerCase()} encontrado`} description="Use o cadastro para incluir o primeiro registro." />}
  </section>;
}
