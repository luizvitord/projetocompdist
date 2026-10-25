import { FormEvent, useEffect, useState } from 'react';
import { Button } from '../../components/ui/Button';
import { DataTable } from '../../components/ui/DataTable';
import { Dialog } from '../../components/ui/Dialog';
import { api, isApiProblem } from '../../services/http';
import { toPage, type Page, type SpringPagedModel } from '../../services/pagination';
import type { Avaliacao } from '../../types/escolar';

export function AvaliacoesPage() {
  const [page, setPage] = useState<Page<Avaliacao> | null>(null); const [message, setMessage] = useState('');
  useEffect(() => { api.get<SpringPagedModel<Avaliacao>>('/avaliacoes', { query: { page: 0, size: 20 } }).then((data) => setPage(toPage(data))).catch(() => setMessage('Não foi possível carregar as avaliações.')); }, []);
  async function grades(event: FormEvent<HTMLFormElement>, id: number) { event.preventDefault(); const form = new FormData(event.currentTarget); const notas = [{ matriculaId: Number(form.get('matriculaId')), valor: Number(form.get('valor')) }]; try { await api.post(`/avaliacoes/${id}/notas`, notas); setMessage('Nota lançada.'); } catch (error) { setMessage(isApiProblem(error) ? error.fieldErrors['notas[0].valor'] ?? error.detail : 'Não foi possível lançar a nota.'); } }
  return <section className="space-y-5"><header><h1 className="text-2xl font-bold text-navy">Avaliações e notas</h1><p className="text-sm text-slate-600">Acompanhe avaliações e faça lançamentos em lote.</p></header>{message ? <p role="status" className="rounded-lg bg-slate-100 p-3 text-sm">{message}</p> : null}{page ? <DataTable items={page.content} keyFor={(item) => item.id} columns={[{ header: 'Descrição', render: (item) => item.descricao }, { header: 'Turma', render: (item) => item.turmaId }, { header: 'Disciplina', render: (item) => item.disciplinaId }, { header: 'Bimestre', render: (item) => item.bimestre }, { header: 'Ação', render: (item) => <Dialog title="Lançar nota" trigger={<Button>Lançar notas</Button>}><form className="grid gap-3" onSubmit={(event) => void grades(event, item.id)}><label>ID da matrícula<input required name="matriculaId" type="number" className="mt-1 w-full rounded border p-2" /></label><label>Valor<input required name="valor" type="number" step="0.01" className="mt-1 w-full rounded border p-2" /></label><Button type="submit">Salvar nota</Button></form></Dialog> }]} /> : <p>Carregando avaliações...</p>}</section>;
}
