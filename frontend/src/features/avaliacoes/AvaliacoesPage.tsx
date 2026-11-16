import { FormEvent, useEffect, useState } from 'react';
import { TurmaCombobox } from '../../components/busca/TurmaCombobox';
import { rotuloTurma, useTurmasPorId } from '../../components/busca/useTurmasPorId';
import { useFeedback } from '../../components/feedback/FeedbackProvider';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { DataTable } from '../../components/ui/DataTable';
import { Dialog } from '../../components/ui/Dialog';
import { EmptyState } from '../../components/ui/EmptyState';
import { Pagination } from '../../components/ui/Pagination';
import { SearchInput } from '../../components/ui/SearchInput';
import { SemResultados } from '../../components/ui/SemResultados';
import { useBuscaNaUrl } from '../../components/ui/useBuscaNaUrl';
import { api, isApiProblem } from '../../services/http';
import { buscarTodasAsPaginas } from '../../services/paginas';
import { toPage, type Page, type SpringPagedModel } from '../../services/pagination';
import type { Aluno } from '../../types/aluno';
import type { Avaliacao } from '../../types/escolar';
import type { Turma } from '../../types/turma';

type AvaliacaoDraft = Omit<Avaliacao, 'id' | 'createdAt' | 'updatedAt'>;

export const TAMANHO_PAGINA_AVALIACOES = 20;
/** Mais recentes primeiro; o id desempata avaliações da mesma data. Enviado pela query (a API mantém o padrão dela). */
export const ORDEM_AVALIACOES = ['data,desc', 'id,desc'];

function AvaliacaoForm({ avaliacao, turmaInicial, onSubmit }: { avaliacao?: Avaliacao; turmaInicial?: Turma | null; onSubmit: (draft: AvaliacaoDraft) => Promise<void> }) {
  const [turma, setTurma] = useState<Turma | null>(turmaInicial ?? null);
  const [disciplinaId, setDisciplinaId] = useState(avaliacao?.disciplinaId ?? 0);
  const [saving, setSaving] = useState(false);
  const turmaId = turma?.id ?? 0;
  const disciplinas = turma?.disciplinas ?? [];
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    if (!Number(form.get('turmaId'))) return;
    setSaving(true);
    try {
      await onSubmit({ turmaId: Number(form.get('turmaId')), disciplinaId: Number(form.get('disciplinaId')), bimestre: Number(form.get('bimestre')), tipo: String(form.get('tipo')), descricao: String(form.get('descricao')), peso: Number(form.get('peso')), notaMaxima: Number(form.get('notaMaxima')), data: String(form.get('data')) });
    } finally { setSaving(false); }
  }
  return <form className="grid gap-3 sm:grid-cols-2" onSubmit={(event) => void submit(event)}>
    <div className="sm:col-span-2"><TurmaCombobox required label="Turma da avaliação" defaultSelected={turmaInicial} onSelect={(item) => { setTurma(item); setDisciplinaId(0); }} /></div>
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

type Filtros = { turma: Turma | null; disciplinaId: string; bimestre: string };
const SEM_FILTROS: Filtros = { turma: null, disciplinaId: '', bimestre: '' };

export function AvaliacoesPage() {
  const [page, setPage] = useState<Page<Avaliacao> | null>(null);
  // Busca e página na URL; a busca é feita no servidor (descrição, turma e disciplina)
  const { busca, pagina, setBusca, setPagina } = useBuscaNaUrl();
  const [filtros, setFiltros] = useState<Filtros>(SEM_FILTROS);
  // Recria o campo de turma do filtro ao limpar (ele guarda a turma escolhida)
  const [versaoFiltros, setVersaoFiltros] = useState(0);
  // Avaliação recém-salva: fica visível no topo da página 1 mesmo se a ordem ou os filtros a deixarem de fora
  const [destaque, setDestaque] = useState<Avaliacao | null>(null);
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
  const turmaFiltro = filtros.turma?.id;

  useEffect(() => {
    const controller = new AbortController();
    setListError('');
    api.get<SpringPagedModel<Avaliacao>>('/avaliacoes', { signal: controller.signal, query: { busca, turmaId: turmaFiltro, disciplinaId: filtros.disciplinaId, bimestre: filtros.bimestre, page: pagina, size: TAMANHO_PAGINA_AVALIACOES, sort: ORDEM_AVALIACOES } })
      .then((data) => setPage(toPage(data)))
      .catch(() => { if (!controller.signal.aborted) setListError('Não foi possível carregar as avaliações.'); });
    return () => controller.abort();
  }, [reload, busca, pagina, turmaFiltro, filtros.disciplinaId, filtros.bimestre]);

  const itens = page ? (destaque && pagina === 0 && !page.content.some((item) => item.id === destaque.id) ? [destaque, ...page.content] : page.content) : [];
  // Só as turmas citadas na página exibida; a escolha de turma no formulário é feita por busca
  const turmas = useTurmasPorId(itens.map((item) => item.turmaId));
  const disciplinaLabel = (turmaId: number, disciplinaId: number) => { const turma = turmas[turmaId]; if (turma === undefined) return 'Carregando...'; const disciplina = turma?.disciplinas.find((item) => item.disciplinaId === disciplinaId); return disciplina ? `${disciplina.codigo} — ${disciplina.nome}` : `Disciplina #${disciplinaId}`; };

  function mudarPagina(numero: number) { setDestaque(null); setPagina(numero); }
  function mudarBusca(texto: string) { setDestaque(null); setBusca(texto); }
  function mudarFiltros(novos: Partial<Filtros>) { setDestaque(null); setPagina(0); setFiltros((atual) => ({ ...atual, ...novos })); }
  function limparFiltros() { setDestaque(null); setPagina(0); setFiltros(SEM_FILTROS); setVersaoFiltros((valor) => valor + 1); }

  async function save(draft: AvaliacaoDraft, item?: Avaliacao) {
    try {
      const salva = item ? await api.put<Avaliacao>(`/avaliacoes/${item.id}`, draft) : await api.post<Avaliacao>('/avaliacoes', draft);
      setCreateOpen(false); setEditing(null); showSuccess(`Avaliação ${item ? 'atualizada' : 'criada'} com sucesso.`);
      // Volta para a página 1 com a avaliação salva visível
      setDestaque(salva ?? null); setPagina(0); refresh();
    } catch (error) { showError(isApiProblem(error) ? error.detail : 'Não foi possível salvar a avaliação.'); }
  }
  async function remove(item: Avaliacao) {
    try { await api.delete(`/avaliacoes/${item.id}`); showSuccess('Avaliação excluída com sucesso.'); if (destaque?.id === item.id) setDestaque(null); refresh(); }
    catch (error) { showError(isApiProblem(error) ? error.detail : 'Não foi possível excluir a avaliação.'); }
  }
  async function abrirLancamento(avaliacao: Avaliacao) {
    setAvaliacaoAberta(avaliacao); setAlunos([]); setNotas({});
    // Todos os alunos da turma (todas as páginas): a capacidade da turma não tem limite máximo na API
    try { setAlunos(await buscarTodasAsPaginas<Aluno>('/alunos', { turmaId: avaliacao.turmaId, sort: ['nome,asc', 'id,asc'] })); }
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

  const disciplinasDoFiltro = filtros.turma?.disciplinas ?? [];
  const temFiltro = Boolean(filtros.turma || filtros.disciplinaId || filtros.bimestre);
  const filtrosUi = <div className="grid gap-3 rounded-lg bg-white p-4 shadow-sm lg:grid-cols-[2fr_1fr_1fr_auto] lg:items-end">
    <TurmaCombobox key={versaoFiltros} label="Filtrar por turma" onSelect={(turma) => mudarFiltros({ turma, disciplinaId: '' })} />
    <label>Disciplina<select aria-label="Filtrar por disciplina" className="mt-1 w-full rounded border p-2" value={filtros.disciplinaId} disabled={!filtros.turma} onChange={(event) => mudarFiltros({ disciplinaId: event.target.value })}><option value="">{filtros.turma ? 'Todas' : 'Escolha a turma'}</option>{disciplinasDoFiltro.map((item) => <option key={item.disciplinaId} value={item.disciplinaId}>{item.codigo} — {item.nome}</option>)}</select></label>
    <label>Bimestre<select aria-label="Filtrar por bimestre" className="mt-1 w-full rounded border p-2" value={filtros.bimestre} onChange={(event) => mudarFiltros({ bimestre: event.target.value })}><option value="">Todos</option>{[1, 2, 3, 4].map((bimestre) => <option key={bimestre} value={bimestre}>{bimestre}º bimestre</option>)}</select></label>
    <Button type="button" variant="secondary" disabled={!temFiltro} onClick={limparFiltros}>Limpar filtros</Button>
  </div>;

  const tabela = page ? itens.length ? <><DataTable items={itens} keyFor={(item) => item.id} columns={[{ header: 'Descrição', render: (item) => <>{item.descricao}{item.id === destaque?.id ? <span className="ml-2 rounded bg-teal-100 px-2 py-0.5 text-xs font-semibold text-teal-800">salva agora</span> : null}</> }, { header: 'Data', render: (item) => item.data ? new Date(`${item.data}T00:00:00`).toLocaleDateString('pt-BR') : '' }, { header: 'Turma', render: (item) => rotuloTurma(turmas, item.turmaId) }, { header: 'Disciplina', render: (item) => disciplinaLabel(item.turmaId, item.disciplinaId) }, { header: 'Bimestre', render: (item) => item.bimestre }, { header: 'Ações', render: (item) => <div className="flex flex-wrap gap-2"><Button variant="secondary" onClick={() => setEditing(item)}>Editar</Button><ConfirmDialog title="Excluir avaliação" description="A avaliação e suas notas serão removidas quando o backend permitir esta operação." confirmLabel="Excluir" trigger={<Button variant="secondary">Excluir</Button>} onConfirm={() => remove(item)} /><Dialog title="Lançar notas" open={avaliacaoAberta?.id === item.id} onOpenChange={(open) => { if (!open) setAvaliacaoAberta(null); }} trigger={<Button onClick={() => void abrirLancamento(item)}>Lançar notas</Button>}><form className="grid gap-3" onSubmit={(event) => void grades(event, item)}>{alunos.length ? alunos.map((aluno) => <label key={aluno.matricula}>{aluno.nome} — {aluno.matricula}<input aria-label={`Nota de ${aluno.nome}`} type="number" min="0" step="0.01" value={notas[aluno.matricula] ?? ''} onChange={(event) => setNotas((atual) => ({ ...atual, [aluno.matricula]: event.target.value }))} className="mt-1 w-full rounded border p-2" disabled={savingGrades} /></label>) : <p className="text-sm text-slate-600">Carregando alunos matriculados...</p>}<Button disabled={!alunos.length || avaliacaoAberta?.id !== item.id || savingGrades} type="submit">{savingGrades ? 'Salvando...' : 'Salvar notas'}</Button></form></Dialog></div> }]} /><Pagination page={page.number} totalPages={page.totalPages} totalElements={page.totalElements} onPageChange={mudarPagina} /></>
    : busca ? <SemResultados busca={busca} onLimpar={() => mudarBusca('')} />
    : temFiltro ? <EmptyState title="Nenhuma avaliação com esses filtros" description="Mude ou limpe os filtros para ver outras avaliações." actionLabel="Limpar filtros" onAction={limparFiltros} />
      : <EmptyState title="Nenhuma avaliação cadastrada" description="Crie a primeira avaliação para lançar notas." />
    : <p role="status">Carregando avaliações...</p>;

  return <section className="space-y-5"><header className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-bold text-navy">Avaliações e notas</h1><p className="text-sm text-slate-600">Crie avaliações e lance as notas dos alunos matriculados. As mais recentes aparecem primeiro.</p></div><Dialog title="Nova avaliação" open={createOpen} onOpenChange={setCreateOpen} trigger={<Button>Nova avaliação</Button>}><AvaliacaoForm onSubmit={save} /></Dialog></header>
    <SearchInput value={busca} onSearch={mudarBusca} placeholder="Buscar por descrição, turma ou disciplina" label="Buscar avaliações" />
    {filtrosUi}
    {listError ? <div role="alert" className="rounded-lg bg-rose-50 p-4 text-rose-800"><p>{listError}</p><Button className="mt-3" type="button" variant="secondary" onClick={refresh}>Tentar novamente</Button></div> : tabela}
    <Dialog title="Editar avaliação" open={Boolean(editing)} onOpenChange={(open) => { if (!open) setEditing(null); }} trigger={<span />}>{editing ? <AvaliacaoForm key={`${editing.id}-${turmas[editing.turmaId] ? 'turma' : 'sem-turma'}`} avaliacao={editing} turmaInicial={turmas[editing.turmaId]} onSubmit={(draft) => save(draft, editing)} /> : null}</Dialog>
  </section>;
}
