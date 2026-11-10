import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useFeedback } from '../../components/feedback/FeedbackProvider';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { DataTable } from '../../components/ui/DataTable';
import { Dialog } from '../../components/ui/Dialog';
import { api, isApiProblem } from '../../services/http';
import { toPage, type Page, type SpringPagedModel } from '../../services/pagination';
import type { Aluno } from '../../types/aluno';
import type { Avaliacao } from '../../types/escolar';
import type { Turma } from '../../types/turma';

type AvaliacaoDraft = Omit<Avaliacao, 'id' | 'createdAt' | 'updatedAt'>;

function AvaliacaoForm({ avaliacao, turmas, onSubmit }: { avaliacao?: Avaliacao; turmas: Turma[]; onSubmit: (draft: AvaliacaoDraft) => Promise<void> }) {
  const [turmaId, setTurmaId] = useState(avaliacao?.turmaId ?? 0);
  const [disciplinaId, setDisciplinaId] = useState(avaliacao?.disciplinaId ?? 0);
  const [saving, setSaving] = useState(false);
  const turma = useMemo(() => turmas.find((item) => item.id === turmaId), [turmas, turmaId]);
  const disciplinas = turma?.disciplinas ?? [];
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSaving(true);
    try {
      await onSubmit({ turmaId: Number(form.get('turmaId')), disciplinaId: Number(form.get('disciplinaId')), bimestre: Number(form.get('bimestre')), tipo: String(form.get('tipo')), descricao: String(form.get('descricao')), peso: Number(form.get('peso')), notaMaxima: Number(form.get('notaMaxima')), data: String(form.get('data')) });
    } finally { setSaving(false); }
  }
  return <form className="grid gap-3 sm:grid-cols-2" onSubmit={(event) => void submit(event)}>
    <label>Turma<select required name="turmaId" aria-label="Turma da avaliação" className="mt-1 w-full rounded border p-2" value={turmaId || ''} onChange={(event) => { setTurmaId(Number(event.target.value)); setDisciplinaId(0); }}><option value="">Selecione a turma</option>{turmas.map((item) => <option key={item.id} value={item.id}>{item.codigo} — {item.nome} — {item.anoLetivo}</option>)}</select></label>
    <label>Disciplina<select required name="disciplinaId" aria-label="Disciplina da avaliação" className="mt-1 w-full rounded border p-2" value={disciplinaId || ''} onChange={(event) => setDisciplinaId(Number(event.target.value))} disabled={!turmaId || !disciplinas.length}><option value="">{turmaId ? 'Selecione a disciplina' : 'Selecione primeiro a turma'}</option>{disciplinas.map((item) => <option key={item.disciplinaId} value={item.disciplinaId}>{item.codigo} — {item.nome}</option>)}</select></label>
    <label>Descrição<input required name="descricao" defaultValue={avaliacao?.descricao} className="mt-1 w-full rounded border p-2" /></label>
    <label>Tipo<select required name="tipo" defaultValue={avaliacao?.tipo ?? 'PROVA'} className="mt-1 w-full rounded border p-2">{['PROVA', 'TRABALHO', 'ATIVIDADE', 'RECUPERACAO'].map((tipo) => <option key={tipo} value={tipo}>{tipo}</option>)}</select></label>
    <label>Bimestre<select required name="bimestre" defaultValue={avaliacao?.bimestre ?? 1} className="mt-1 w-full rounded border p-2">{[1, 2, 3, 4].map((bimestre) => <option key={bimestre} value={bimestre}>{bimestre}º bimestre</option>)}</select></label>
    <label>Data<input required name="data" type="date" defaultValue={avaliacao?.data} className="mt-1 w-full rounded border p-2" /></label>
    <label>Peso<input required name="peso" type="number" min="0.01" step="0.01" defaultValue={avaliacao?.peso ?? 1} className="mt-1 w-full rounded border p-2" /></label>
    <label>Nota máxima<input required name="notaMaxima" type="number" min="0.01" step="0.01" defaultValue={avaliacao?.notaMaxima ?? 10} className="mt-1 w-full rounded border p-2" /></label>
    <Button className="sm:col-span-2" disabled={saving} type="submit">{saving ? 'Salvando...' : 'Salvar avaliação'}</Button>
  </form>;
}

export function AvaliacoesPage() {
  const [page, setPage] = useState<Page<Avaliacao> | null>(null);
  const [turmas, setTurmas] = useState<Turma[]>([]);
  const [alunos, setAlunos] = useState<Aluno[]>([]);
  const [avaliacaoAberta, setAvaliacaoAberta] = useState<Avaliacao | null>(null);
  const [notas, setNotas] = useState<Record<string, string>>({});
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<Avaliacao | null>(null);
  const [reload, setReload] = useState(0);
  const [listError, setListError] = useState('');
  const [savingGrades, setSavingGrades] = useState(false);
  const { showError, showSuccess } = useFeedback();
  const refresh = () => setReload((value) => value + 1);
  useEffect(() => {
    setListError('');
    api.get<SpringPagedModel<Avaliacao>>('/avaliacoes', { query: { page: 0, size: 20 } }).then((data) => setPage(toPage(data))).catch(() => setListError('Não foi possível carregar as avaliações.'));
    api.get<SpringPagedModel<Turma>>('/turmas', { query: { page: 0, size: 100 } }).then((data) => setTurmas(toPage(data).content)).catch(() => showError('Não foi possível carregar as turmas disponíveis.'));
  }, [reload, showError]);
  const turmaLabel = (id: number) => { const turma = turmas.find((item) => item.id === id); return turma ? `${turma.codigo} — ${turma.nome}` : 'Turma não encontrada'; };
  const disciplinaLabel = (turmaId: number, disciplinaId: number) => { const disciplina = turmas.find((item) => item.id === turmaId)?.disciplinas.find((item) => item.disciplinaId === disciplinaId); return disciplina ? `${disciplina.codigo} — ${disciplina.nome}` : 'Disciplina não encontrada'; };
  async function save(draft: AvaliacaoDraft, item?: Avaliacao) {
    try {
      if (item) await api.put(`/avaliacoes/${item.id}`, draft); else await api.post('/avaliacoes', draft);
      setCreateOpen(false); setEditing(null); showSuccess(`Avaliação ${item ? 'atualizada' : 'criada'} com sucesso.`); refresh();
    } catch (error) { showError(isApiProblem(error) ? error.detail : 'Não foi possível salvar a avaliação.'); }
  }
  async function remove(item: Avaliacao) {
    try { await api.delete(`/avaliacoes/${item.id}`); showSuccess('Avaliação excluída com sucesso.'); refresh(); }
    catch (error) { showError(isApiProblem(error) ? error.detail : 'Não foi possível excluir a avaliação.'); }
  }
  async function abrirLancamento(avaliacao: Avaliacao) {
    setAvaliacaoAberta(avaliacao); setAlunos([]); setNotas({});
    try { const data = await api.get<SpringPagedModel<Aluno>>('/alunos', { query: { turmaId: avaliacao.turmaId, page: 0, size: 100 } }); setAlunos(toPage(data).content); }
    catch { showError('Não foi possível carregar os alunos matriculados nesta turma.'); }
  }
  async function grades(event: FormEvent<HTMLFormElement>, avaliacao: Avaliacao) {
    event.preventDefault();
    if (savingGrades) return;
    const payload = alunos.filter((aluno) => notas[aluno.matricula] !== undefined && notas[aluno.matricula] !== '').map((aluno) => ({ matriculaAluno: aluno.matricula, valor: Number(notas[aluno.matricula]) }));
    if (!payload.length) { showError('Informe ao menos uma nota antes de salvar.'); return; }
    setSavingGrades(true);
    try { await api.post(`/avaliacoes/${avaliacao.id}/notas`, payload); setAvaliacaoAberta(null); showSuccess('Notas lançadas com sucesso.'); }
    catch (error) { showError(isApiProblem(error) ? error.detail : 'Não foi possível lançar as notas.'); } finally { setSavingGrades(false); }
  }
  return <section className="space-y-5"><header className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-bold text-navy">Avaliações e notas</h1><p className="text-sm text-slate-600">Crie avaliações e lance as notas dos alunos matriculados.</p></div><Dialog title="Nova avaliação" open={createOpen} onOpenChange={setCreateOpen} trigger={<Button>Nova avaliação</Button>}><AvaliacaoForm turmas={turmas} onSubmit={save} /></Dialog></header>
    {listError ? <div role="alert" className="rounded-lg bg-rose-50 p-4 text-rose-800"><p>{listError}</p><Button className="mt-3" type="button" variant="secondary" onClick={refresh}>Tentar novamente</Button></div> : page ? <DataTable items={page.content} keyFor={(item) => item.id} columns={[{ header: 'Descrição', render: (item) => item.descricao }, { header: 'Turma', render: (item) => turmaLabel(item.turmaId) }, { header: 'Disciplina', render: (item) => disciplinaLabel(item.turmaId, item.disciplinaId) }, { header: 'Bimestre', render: (item) => item.bimestre }, { header: 'Ações', render: (item) => <div className="flex flex-wrap gap-2"><Button variant="secondary" onClick={() => setEditing(item)}>Editar</Button><ConfirmDialog title="Excluir avaliação" description="A avaliação e suas notas serão removidas quando o backend permitir esta operação." confirmLabel="Excluir" trigger={<Button variant="secondary">Excluir</Button>} onConfirm={() => remove(item)} /><Dialog title="Lançar notas" open={avaliacaoAberta?.id === item.id} onOpenChange={(open) => { if (!open) setAvaliacaoAberta(null); }} trigger={<Button onClick={() => void abrirLancamento(item)}>Lançar notas</Button>}><form className="grid gap-3" onSubmit={(event) => void grades(event, item)}>{alunos.length ? alunos.map((aluno) => <label key={aluno.matricula}>{aluno.nome} — {aluno.matricula}<input aria-label={`Nota de ${aluno.nome}`} type="number" min="0" step="0.01" value={notas[aluno.matricula] ?? ''} onChange={(event) => setNotas((atual) => ({ ...atual, [aluno.matricula]: event.target.value }))} className="mt-1 w-full rounded border p-2" disabled={savingGrades} /></label>) : <p className="text-sm text-slate-600">Carregando alunos matriculados...</p>}<Button disabled={!alunos.length || avaliacaoAberta?.id !== item.id || savingGrades} type="submit">{savingGrades ? 'Salvando...' : 'Salvar notas'}</Button></form></Dialog></div> }]} /> : <p role="status">Carregando avaliações...</p>}
    <Dialog title="Editar avaliação" open={Boolean(editing)} onOpenChange={(open) => { if (!open) setEditing(null); }} trigger={<span />}>{editing ? <AvaliacaoForm avaliacao={editing} turmas={turmas} onSubmit={(draft) => save(draft, editing)} /> : null}</Dialog>
  </section>;
}
