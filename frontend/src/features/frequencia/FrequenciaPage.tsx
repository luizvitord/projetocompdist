import { FormEvent, useEffect, useState } from 'react';
import { useFeedback } from '../../components/feedback/FeedbackProvider';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { api, isApiProblem } from '../../services/http';
import { toPage, type SpringPagedModel } from '../../services/pagination';
import type { Aluno } from '../../types/aluno';
import type { StatusPresenca } from '../../types/escolar';
import type { Turma } from '../../types/turma';

type RegistroEmEdicao = { status: StatusPresenca; observacao: string };
const descricaoTurma = (turma: Turma) => `${turma.codigo} — ${turma.nome} — ${turma.anoLetivo} — ${turma.turno}`;

export function FrequenciaPage() {
  const [turmas, setTurmas] = useState<Turma[]>([]); const [turmaId, setTurmaId] = useState(''); const [turma, setTurma] = useState<Turma | null>(null); const [alunos, setAlunos] = useState<Aluno[]>([]); const [disciplinaId, setDisciplinaId] = useState(''); const [registros, setRegistros] = useState<Record<string, RegistroEmEdicao>>({}); const [report, setReport] = useState<{ alunos: { nome: string; percentual: number; abaixoDoMinimo: boolean }[] } | null>(null); const [savingCall, setSavingCall] = useState(false); const [loadingReport, setLoadingReport] = useState(false);
  const { showError, showSuccess } = useFeedback();

  useEffect(() => { api.get<SpringPagedModel<Turma>>('/turmas', { query: { page: 0, size: 100 } }).then((data) => setTurmas(toPage(data).content)).catch(() => showError('Não foi possível carregar as turmas.')); }, [showError]);
  useEffect(() => {
    if (!turmaId) { setTurma(null); setAlunos([]); setDisciplinaId(''); setRegistros({}); return; }
    void Promise.all([api.get<Turma>(`/turmas/${turmaId}`), api.get<SpringPagedModel<Aluno>>('/alunos', { query: { turmaId, page: 0, size: 100 } })]).then(([turmaSelecionada, paginaAlunos]) => {
      const alunosDaTurma = toPage(paginaAlunos).content;
      setTurma(turmaSelecionada); setAlunos(alunosDaTurma); setDisciplinaId('');
      setRegistros(Object.fromEntries(alunosDaTurma.map((aluno) => [aluno.matricula, { status: 'PRESENTE', observacao: '' }])));
    }).catch(() => showError('Não foi possível carregar as disciplinas e os alunos da turma.'));
  }, [turmaId, showError]);

  const disciplina = turma?.disciplinas.find((item) => item.disciplinaId === Number(disciplinaId));
  function atualizarRegistro(matricula: string, campo: keyof RegistroEmEdicao, valor: string) { setRegistros((atual) => ({ ...atual, [matricula]: { ...atual[matricula], [campo]: valor } })); }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!disciplina || savingCall) return;
    const form = new FormData(event.currentTarget);
    const payload = { turmaId: Number(turmaId), disciplinaId: disciplina.disciplinaId, data: String(form.get('data')), professorId: disciplina.professorId, registros: alunos.map((aluno) => ({ matriculaAluno: aluno.matricula, status: registros[aluno.matricula]?.status ?? 'PRESENTE', observacao: registros[aluno.matricula]?.observacao || null })) };
    setSavingCall(true);
    try { await api.post('/frequencias/chamadas', payload); showSuccess('Chamada registrada com sucesso.'); } catch (error) { showError(isApiProblem(error) ? error.detail : 'Não foi possível registrar a chamada.'); } finally { setSavingCall(false); }
  }
  async function loadReport(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (loadingReport) return; const form = new FormData(event.currentTarget); setLoadingReport(true); try { setReport(await api.get('/frequencias/relatorio', { query: { turmaId: Number(turmaId), disciplinaId: disciplinaId || undefined, dataInicio: String(form.get('dataInicio') || ''), dataFim: String(form.get('dataFim') || '') } })); } catch { showError('Não foi possível gerar o relatório.'); } finally { setLoadingReport(false); } }

  return <section className="space-y-5"><header><h1 className="text-2xl font-bold text-navy">Frequência</h1><p className="text-sm text-slate-600">Selecione a turma para registrar a presença dos alunos matriculados.</p></header><div className="grid gap-5 lg:grid-cols-2"><Card><h2 className="font-bold text-navy">Nova chamada</h2><form className="mt-3 grid gap-3" onSubmit={submit}><label>Turma da chamada<select aria-label="Turma da chamada" required value={turmaId} onChange={(event) => setTurmaId(event.target.value)} className="mt-1 w-full rounded border p-2" disabled={savingCall}><option value="">Selecione uma turma</option>{turmas.map((item) => <option key={item.id} value={item.id}>{descricaoTurma(item)}</option>)}</select></label><label>Disciplina<select required disabled={!turma || savingCall} value={disciplinaId} onChange={(event) => setDisciplinaId(event.target.value)} className="mt-1 w-full rounded border p-2"><option value="">Selecione uma disciplina</option>{turma?.disciplinas.map((item) => <option key={item.disciplinaId} value={item.disciplinaId}>{item.codigo} — {item.nome}</option>)}</select></label>{disciplina ? <p className="rounded bg-slate-100 p-2 text-sm">Professor responsável: {disciplina.professorNome ?? 'Não definido'}</p> : null}<label>Data<input name="data" required type="date" className="mt-1 w-full rounded border p-2" disabled={savingCall} /></label>{alunos.length ? <fieldset className="grid gap-3"><legend className="font-semibold text-slate-800">Alunos matriculados</legend>{alunos.map((aluno) => <div key={aluno.matricula} className="rounded border p-3"><p className="font-medium">{aluno.nome} — {aluno.matricula}</p><div className="mt-2 grid gap-2 sm:grid-cols-2"><label>Status<select aria-label={`Status de ${aluno.nome}`} value={registros[aluno.matricula]?.status ?? 'PRESENTE'} onChange={(event) => atualizarRegistro(aluno.matricula, 'status', event.target.value)} className="mt-1 w-full rounded border p-2" disabled={savingCall}><option value="PRESENTE">Presente</option><option value="FALTA">Falta</option><option value="ATRASO">Atraso</option><option value="JUSTIFICADA">Falta justificada</option></select></label><label>Observação<textarea aria-label={`Observação de ${aluno.nome}`} value={registros[aluno.matricula]?.observacao ?? ''} onChange={(event) => atualizarRegistro(aluno.matricula, 'observacao', event.target.value)} className="mt-1 w-full rounded border p-2" disabled={savingCall} /></label></div></div>)}</fieldset> : turmaId ? <p className="text-sm text-slate-600">Não há alunos com matrícula ativa nesta turma.</p> : null}<Button disabled={!disciplina || !alunos.length || savingCall} type="submit">{savingCall ? 'Registrando...' : 'Registrar chamada'}</Button></form></Card><Card><h2 className="font-bold text-navy">Relatório</h2><form className="mt-3 grid gap-3" onSubmit={loadReport}><p className="text-sm text-slate-600">O relatório usa a turma e, se selecionada, a disciplina da chamada.</p><label>Início<input name="dataInicio" type="date" className="mt-1 w-full rounded border p-2" disabled={loadingReport} /></label><label>Fim<input name="dataFim" type="date" className="mt-1 w-full rounded border p-2" disabled={loadingReport} /></label><Button disabled={!turmaId || loadingReport} type="submit">{loadingReport ? 'Gerando...' : 'Gerar relatório'}</Button></form>{report ? <ul className="mt-4 space-y-2">{report.alunos.map((aluno) => <li key={aluno.nome}>{aluno.nome}: {aluno.percentual}% — {aluno.abaixoDoMinimo ? 'Abaixo da frequência mínima' : 'Frequência adequada'}</li>)}</ul> : null}</Card></div></section>;
}
