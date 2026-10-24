import { FormEvent, useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { api, isApiProblem } from '../../services/http';
import type { Turma } from '../../types/turma';

export function TurmaDetailPage() {
  const { id } = useParams();
  const [turma, setTurma] = useState<Turma | null>(null);
  const [message, setMessage] = useState('');
  const load = useCallback(() => api.get<Turma>(`/turmas/${id}`).then(setTurma).catch(() => setMessage('Não foi possível carregar a turma.')), [id]);
  useEffect(() => { void load(); }, [load]);
  async function offer(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form = new FormData(event.currentTarget); try { await api.put(`/turmas/${id}/disciplinas/${form.get('disciplinaId')}`, { professorId: Number(form.get('professorId')) || null }); setMessage('Disciplina ofertada.'); void load(); } catch (error) { setMessage(isApiProblem(error) ? error.detail : 'Não foi possível ofertar a disciplina.'); } }
  async function removeProfessor(professorId: number) { if (!window.confirm('Remover este professor da turma?')) return; try { await api.delete(`/turmas/${id}/professores/${professorId}`); setMessage('Professor removido.'); void load(); } catch (error) { setMessage(isApiProblem(error) ? error.detail : 'Não foi possível remover o professor.'); } }
  return <section className="space-y-5"><Link className="text-sm font-semibold text-teal-700" to="/turmas">← Voltar para turmas</Link>{message ? <p role="status" className="rounded-lg bg-slate-100 p-3 text-sm text-slate-700">{message}</p> : null}{turma ? <><header><h1 className="text-2xl font-bold text-navy">{turma.codigo} · {turma.nome}</h1><p className="text-slate-600">{turma.anoLetivo} · {turma.turno} · capacidade {turma.capacidade}</p></header><div className="grid gap-5 lg:grid-cols-2"><Card><h2 className="font-bold text-navy">Professores</h2><ul className="mt-3 space-y-2">{turma.professores.map((professor) => <li key={professor.id} className="flex justify-between gap-3"><span>{professor.nome}</span><Button variant="danger" onClick={() => void removeProfessor(professor.id)}>Remover</Button></li>)}</ul></Card><Card><h2 className="font-bold text-navy">Ofertar disciplina</h2><form className="mt-3 grid gap-3" onSubmit={offer}><label className="text-sm">ID da disciplina<input name="disciplinaId" required type="number" className="mt-1 w-full rounded border p-2" /></label><label className="text-sm">ID do professor (opcional)<input name="professorId" type="number" className="mt-1 w-full rounded border p-2" /></label><Button type="submit">Adicionar oferta</Button></form></Card></div><Card><h2 className="font-bold text-navy">Disciplinas ofertadas</h2><ul className="mt-3 divide-y">{turma.disciplinas.map((disciplina) => <li key={disciplina.disciplinaId} className="py-2">{disciplina.codigo} · {disciplina.nome} {disciplina.professorNome ? `— ${disciplina.professorNome}` : '— sem professor definido'}</li>)}</ul></Card></> : <p>Carregando turma...</p>}</section>;
}
