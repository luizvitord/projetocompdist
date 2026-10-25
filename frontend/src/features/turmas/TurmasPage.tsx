import { Link } from 'react-router-dom';
import { RegistryPage } from '../administrativo/RegistryPage';
import type { Turma } from '../../types/turma';

export function TurmasPage() {
  return <RegistryPage<Turma> title="Turmas" singular="turma" path="/turmas" toPayload={(form) => ({ codigo: form.get('codigo'), nome: form.get('nome'), anoLetivo: Number(form.get('anoLetivo')), turno: form.get('turno'), capacidade: Number(form.get('capacidade')) })} fields={[
    { name: 'codigo', label: 'Código', required: true }, { name: 'nome', label: 'Nome', required: true }, { name: 'anoLetivo', label: 'Ano letivo', type: 'number', required: true, defaultValue: String(new Date().getFullYear()) }, { name: 'turno', label: 'Turno', type: 'select', required: true, options: [{ value: 'MANHA', label: 'Manhã' }, { value: 'TARDE', label: 'Tarde' }, { value: 'NOITE', label: 'Noite' }, { value: 'INTEGRAL', label: 'Integral' }] }, { name: 'capacidade', label: 'Capacidade', type: 'number', required: true }
  ]} columns={[{ header: 'Código', render: (item) => <Link className="font-semibold text-teal-700 hover:underline" to={`/turmas/${item.id}`}>{item.codigo}</Link> }, { header: 'Turma', render: (item) => item.nome }, { header: 'Ano', render: (item) => item.anoLetivo }, { header: 'Turno', render: (item) => item.turno }, { header: 'Vagas', render: (item) => item.capacidade }]} />;
}
