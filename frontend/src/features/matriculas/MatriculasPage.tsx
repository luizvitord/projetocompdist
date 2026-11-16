import { FormEvent, useEffect, useState } from 'react';
import { AlunoCombobox } from '../../components/busca/AlunoCombobox';
import { TurmaCombobox } from '../../components/busca/TurmaCombobox';
import { rotuloTurma, useTurmasPorId } from '../../components/busca/useTurmasPorId';
import { useFeedback } from '../../components/feedback/FeedbackProvider';
import { Button } from '../../components/ui/Button';
import { DataTable } from '../../components/ui/DataTable';
import { Dialog } from '../../components/ui/Dialog';
import { EmptyState } from '../../components/ui/EmptyState';
import { Pagination } from '../../components/ui/Pagination';
import { api, isApiProblem } from '../../services/http';
import { toPage, type Page, type SpringPagedModel } from '../../services/pagination';
import type { Matricula } from '../../types/escolar';

export function MatriculasPage() {
  const [page, setPage] = useState<Page<Matricula> | null>(null);
  const [number, setNumber] = useState(0);
  const [reload, setReload] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [statusOpen, setStatusOpen] = useState<number | null>(null);
  const [transferOpen, setTransferOpen] = useState<number | null>(null);
  const [listError, setListError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { showError, showSuccess } = useFeedback();
  // Só as turmas da página exibida; a escolha de turma nos formulários é feita por busca
  const turmas = useTurmasPorId(page?.content.map((item) => item.turmaId) ?? []);

  useEffect(() => {
    setListError('');
    api.get<SpringPagedModel<Matricula>>('/matriculas', { query: { page: number, size: 20, sort: 'id,desc' } })
      .then((data) => setPage(toPage(data))).catch(() => setListError('Não foi possível carregar as matrículas.'));
  }, [number, reload, showError]);

  const refresh = () => setReload((value) => value + 1);
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const form = new FormData(event.currentTarget);
    const matriculaAluno = String(form.get('matriculaAluno') ?? ''); const turmaId = Number(form.get('turmaId'));
    if (!matriculaAluno || !turmaId) { showError('Selecione o aluno e a turma na lista de sugestões.'); return; }
    setSubmitting(true);
    try {
      await api.post('/matriculas', { matriculaAluno, turmaId });
      setCreateOpen(false);
      showSuccess('Matrícula criada com sucesso.');
      refresh();
    } catch (error) { showError(isApiProblem(error) ? error.detail : 'Não foi possível criar a matrícula.'); } finally { setSubmitting(false); }
  }
  async function status(event: FormEvent<HTMLFormElement>, id: number) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    if (submitting) return;
    setSubmitting(true);
    try { await api.patch(`/matriculas/${id}/status`, { status: form.get('status'), motivo: form.get('motivo') }); setStatusOpen(null); showSuccess('Status atualizado com sucesso.'); refresh(); }
    catch (error) { showError(isApiProblem(error) ? error.detail : 'Não foi possível alterar o status.'); } finally { setSubmitting(false); }
  }
  async function transfer(event: FormEvent<HTMLFormElement>, id: number) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    if (submitting) return;
    const turmaDestinoId = Number(form.get('turmaDestinoId'));
    if (!turmaDestinoId) { showError('Selecione a turma de destino na lista de sugestões.'); return; }
    setSubmitting(true);
    try { await api.post(`/matriculas/${id}/transferencia`, { turmaDestinoId, motivo: form.get('motivo') }); setTransferOpen(null); showSuccess('Transferência realizada com sucesso.'); refresh(); }
    catch (error) { showError(isApiProblem(error) ? error.detail : 'Não foi possível transferir.'); } finally { setSubmitting(false); }
  }

  return <section className="space-y-5"><header className="flex items-center justify-between gap-3"><div><h1 className="text-2xl font-bold text-navy">Matrículas</h1><p className="text-sm text-slate-600">Registre, encerre e transfira vínculos escolares. As mais recentes aparecem primeiro.</p></div>
    <Dialog title="Nova matrícula" open={createOpen} onOpenChange={setCreateOpen} trigger={<Button>Nova matrícula</Button>}><form className="grid gap-3" onSubmit={create}>
      <AlunoCombobox required />
      <TurmaCombobox required /><Button disabled={submitting} type="submit">{submitting ? 'Salvando...' : 'Criar matrícula'}</Button>
    </form></Dialog></header>
    {listError ? <div role="alert" className="rounded-lg bg-rose-50 p-4 text-rose-800"><p>{listError}</p><Button className="mt-3" type="button" variant="secondary" onClick={refresh}>Tentar novamente</Button></div> : !page ? <p role="status" className="rounded-lg bg-white p-4 text-slate-600">Carregando matrículas...</p> : page.content.length ? <><DataTable items={page.content} keyFor={(item) => item.id} columns={[
      { header: 'Matrícula', render: (item) => item.matriculaAluno }, { header: 'Aluno', render: (item) => item.alunoNome }, { header: 'Turma', render: (item) => rotuloTurma(turmas, item.turmaId) }, { header: 'Ano', render: (item) => item.anoLetivo }, { header: 'Status', render: (item) => item.status },
      { header: 'Ações', render: (item) => <div className="flex gap-2"><Dialog title="Alterar status" open={statusOpen === item.id} onOpenChange={(open) => setStatusOpen(open ? item.id : null)} trigger={<Button variant="secondary">Status</Button>}><form className="grid gap-3" onSubmit={(event) => void status(event, item.id)}><label>Novo status<select required name="status" className="mt-1 w-full rounded border p-2" defaultValue=""><option value="">Selecione o status</option>{['TRANCADA', 'CONCLUIDA', 'CANCELADA'].map((statusOption) => <option key={statusOption} value={statusOption}>{statusOption}</option>)}</select></label><label>Motivo<textarea name="motivo" className="mt-1 w-full rounded border p-2" /></label><Button disabled={submitting} type="submit">{submitting ? 'Salvando...' : 'Confirmar'}</Button></form></Dialog><Dialog title="Transferir matrícula" open={transferOpen === item.id} onOpenChange={(open) => setTransferOpen(open ? item.id : null)} trigger={<Button variant="secondary">Transferir</Button>}><form className="grid gap-3" onSubmit={(event) => void transfer(event, item.id)}><TurmaCombobox required name="turmaDestinoId" label="Turma de destino" excluirId={item.turmaId} anoFixo={item.anoLetivo} /><label>Motivo<textarea required name="motivo" className="mt-1 w-full rounded border p-2" /></label><Button disabled={submitting} type="submit">{submitting ? 'Salvando...' : 'Transferir'}</Button></form></Dialog></div> }
    ]} /><Pagination page={page.number} totalPages={page.totalPages} totalElements={page.totalElements} onPageChange={setNumber} /></> : <EmptyState title="Nenhuma matrícula encontrada" description="Crie a primeira matrícula para começar." />}
  </section>;
}
