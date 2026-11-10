import { FormEvent, InputHTMLAttributes, useEffect, useMemo, useRef, useState } from 'react';
import { Button } from '../../components/ui/Button';
import { DataTable, type DataTableColumn } from '../../components/ui/DataTable';
import { Dialog } from '../../components/ui/Dialog';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { EmptyState } from '../../components/ui/EmptyState';
import { Pagination } from '../../components/ui/Pagination';
import { useFeedback } from '../../components/feedback/FeedbackProvider';
import { api, isApiProblem } from '../../services/http';
import { toPage, type Page, type SpringPagedModel } from '../../services/pagination';
import { formatTelefone, isValidTelefone, normalizeTelefone } from '../../services/telefone';

export type FieldOption = { value: string; label: string };
export type Field = { name: string; label: string; type?: 'text' | 'email' | 'number' | 'date' | 'checkbox' | 'select' | 'cpf' | 'phone'; required?: boolean; defaultValue?: string | boolean; options?: FieldOption[] };
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

function PhoneInput({ defaultValue, ...props }: Omit<InputHTMLAttributes<HTMLInputElement>, 'defaultValue' | 'value' | 'onChange'> & { defaultValue?: string }) {
  const [value, setValue] = useState(() => formatTelefone(defaultValue ?? ''));

  useEffect(() => setValue(formatTelefone(defaultValue ?? '')), [defaultValue]);

  return <input {...props} type="tel" inputMode="tel" autoComplete="tel" value={value} onChange={(event) => setValue(formatTelefone(event.target.value))} />;
}

export function RegistryPage<T extends Resource>({ title, singular, path, columns, fields, filters = EMPTY_FILTERS, toPayload, deleteEnabled = true }: {
  title: string; singular: string; path: string; columns: DataTableColumn<T>[]; fields: Field[]; filters?: Filters; toPayload?: (form: FormData) => Record<string, unknown>; deleteEnabled?: boolean;
}) {
  const [pageNumber, setPageNumber] = useState(0);
  const [page, setPage] = useState<Page<T> | null>(null);
  const [error, setError] = useState('');
  const [formError, setFormError] = useState<Record<string, string>>({});
  const [refresh, setRefresh] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<T | null>(null);
  const [submitting, setSubmitting] = useState<'create' | 'update' | null>(null);
  const submittingRef = useRef(false);
  const { showError, showSuccess } = useFeedback();
  const singularLabel = `${singular.charAt(0).toUpperCase()}${singular.slice(1)}`;
  const filtersKey = JSON.stringify(filters);
  const stableFilters = useMemo(() => JSON.parse(filtersKey) as Filters, [filtersKey]);

  useEffect(() => {
    setError('');
    api.get<SpringPagedModel<T>>(path, { query: { ...stableFilters, page: pageNumber, size: 20 } })
      .then((response) => { setPage(toPage(response)); setError(''); })
      .catch(() => setError(`Não foi possível carregar ${title.toLowerCase()}.`));
  }, [pageNumber, path, refresh, stableFilters, title]);

  function validateForm(form: FormData) {
    const validationErrors: Record<string, string> = {};
    for (const field of fields) {
      const value = String(form.get(field.name) ?? '');
      if (field.type === 'cpf' && !isValidCpf(value)) validationErrors[field.name] = 'Informe um CPF válido.';
      if (field.type === 'phone' && !isValidTelefone(value)) validationErrors[field.name] = 'Informe um telefone válido com DDD.';
    }
    return validationErrors;
  }

  function payloadFrom(form: FormData) {
    const payload = toPayload ? toPayload(form) : Object.fromEntries(form.entries());
    for (const field of fields) {
      if (field.type === 'phone' && typeof payload[field.name] === 'string') payload[field.name] = normalizeTelefone(payload[field.name] as string);
    }
    return payload;
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current) return;
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const validationErrors = validateForm(form);
    if (Object.keys(validationErrors).length > 0) {
      setFormError(validationErrors);
      return;
    }
    submittingRef.current = true;
    setSubmitting('create');
    try {
      await api.post<T>(path, payloadFrom(form));
      setFormError({});
      formElement.reset();
      setCreateOpen(false);
      showSuccess(`${singularLabel} cadastrado(a) com sucesso.`);
      setRefresh((value) => value + 1);
    } catch (reason) {
      setFormError(isApiProblem(reason) ? reason.fieldErrors : { form: 'Não foi possível salvar os dados.' });
    } finally {
      submittingRef.current = false;
      setSubmitting(null);
    }
  }

  async function update(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing || submittingRef.current) return;
    const form = new FormData(event.currentTarget);
    const validationErrors = validateForm(form);
    if (Object.keys(validationErrors).length > 0) {
      setFormError(validationErrors);
      return;
    }
    submittingRef.current = true;
    setSubmitting('update');
    try {
      await api.put<T>(`${path}/${editing.id}`, payloadFrom(form));
      setFormError({});
      setEditing(null);
      showSuccess(`${singularLabel} atualizado(a) com sucesso.`);
      setRefresh((value) => value + 1);
    } catch (reason) {
      setFormError(isApiProblem(reason) ? reason.fieldErrors : { form: 'Não foi possível salvar os dados.' });
    } finally {
      submittingRef.current = false;
      setSubmitting(null);
    }
  }

  async function remove(item: T) {
    try {
      await api.delete(`${path}/${item.id}`);
      showSuccess(`${singularLabel} excluído(a) com sucesso.`);
      setRefresh((value) => value + 1);
    } catch (reason) {
      showError(isApiProblem(reason) ? reason.detail : `Não foi possível excluir ${singular.toLowerCase()}.`);
    }
  }

  function fieldValue(field: Field, item?: T | null) {
    const value = item?.[field.name] ?? field.defaultValue;
    return value === undefined || value === null ? '' : String(value);
  }

  function form(onSubmit: (event: FormEvent<HTMLFormElement>) => void, mode: 'create' | 'update', item?: T | null) {
    const isSubmitting = submitting === mode;
    return <form className="grid gap-3 sm:grid-cols-2" onSubmit={onSubmit}>
      {fields.map((field) => <label key={field.name} className="text-sm font-medium text-slate-700">{field.label}
        {field.type === 'checkbox' ? <input className="ml-2" name={field.name} type="checkbox" defaultChecked={Boolean(item?.[field.name] ?? field.defaultValue)} disabled={isSubmitting} /> : field.type === 'select' ? <select className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" name={field.name} required={field.required} defaultValue={fieldValue(field, item)} disabled={isSubmitting}><option value="">Selecione</option>{field.options?.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select> : field.type === 'phone' ? <PhoneInput className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" name={field.name} required={field.required} defaultValue={fieldValue(field, item)} disabled={isSubmitting} placeholder="(00) 00000-0000" maxLength={15} /> : <input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" name={field.name} type={field.type === 'cpf' ? 'text' : field.type ?? 'text'} inputMode={field.type === 'cpf' ? 'numeric' : undefined} placeholder={field.type === 'cpf' ? '000.000.000-00' : undefined} maxLength={field.type === 'cpf' ? 14 : undefined} required={field.required} defaultValue={fieldValue(field, item)} disabled={isSubmitting} />}
        {formError[field.name] ? <span className="mt-1 block text-xs text-rose-700">{formError[field.name]}</span> : null}
      </label>)}
      {formError.form ? <p role="alert" className="text-sm text-rose-700 sm:col-span-2">{formError.form}</p> : null}
      <Button className="sm:col-span-2" type="submit" disabled={isSubmitting}>{isSubmitting ? 'Salvando...' : 'Salvar'}</Button>
    </form>;
  }

  const tableColumns: DataTableColumn<T>[] = [...columns, { header: 'Ações', render: (item) => <div className="flex flex-wrap gap-2"><Button type="button" variant="secondary" onClick={() => { setFormError({}); setEditing(item); }}>Editar</Button>{deleteEnabled ? <ConfirmDialog title={`Excluir ${singular.toLowerCase()}`} description={`Esta ação excluirá o registro de ${singular.toLowerCase()}.`} confirmLabel="Excluir" trigger={<Button type="button" variant="secondary">Excluir</Button>} onConfirm={() => remove(item)} /> : null}</div> }];

  return <section className="space-y-5">
    <header className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-bold text-navy">{title}</h1><p className="mt-1 text-sm text-slate-600">Cadastre e acompanhe {title.toLowerCase()}.</p></div>
      <Dialog title={`Novo ${singular}`} open={createOpen} onOpenChange={(open) => { setCreateOpen(open); if (open) setFormError({}); }} trigger={<Button>Novo {singular}</Button>}>{form(create, 'create')}</Dialog>
      <Dialog title={`Editar ${singular}`} open={Boolean(editing)} onOpenChange={(open) => { if (!open) { setEditing(null); setFormError({}); } }} trigger={<span />}>{editing ? form(update, 'update', editing) : null}</Dialog>
    </header>
    {error ? <p role="alert" className="rounded-lg bg-rose-50 p-4 text-rose-800">{error}</p> : null}
    {error ? <Button type="button" variant="secondary" onClick={() => setRefresh((value) => value + 1)}>Tentar novamente</Button> : !page ? <p role="status" className="rounded-lg bg-white p-4 text-slate-600">Carregando {title.toLowerCase()}...</p> : page.content.length > 0 ? <><DataTable columns={tableColumns} items={page.content} keyFor={(item) => item.id} /><Pagination page={page.number} totalPages={page.totalPages} onPageChange={setPageNumber} /></> : <EmptyState title={`Nenhum ${singular.toLowerCase()} encontrado`} description="Use o cadastro para incluir o primeiro registro." />}
  </section>;
}
