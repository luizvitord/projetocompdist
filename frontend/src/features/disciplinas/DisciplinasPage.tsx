import { RegistryPage } from '../administrativo/RegistryPage';
import type { Disciplina } from '../../types/administrativo';

export function DisciplinasPage() {
  return <RegistryPage<Disciplina> title="Disciplinas" singular="disciplina" path="/disciplinas" toPayload={(form) => ({ codigo: form.get('codigo'), nome: form.get('nome'), cargaHoraria: Number(form.get('cargaHoraria')), ativa: form.get('ativa') === 'on' })} fields={[
    { name: 'codigo', label: 'Código', required: true }, { name: 'nome', label: 'Nome', required: true }, { name: 'cargaHoraria', label: 'Carga horária', type: 'number', required: true }, { name: 'ativa', label: 'Ativa', type: 'checkbox', defaultValue: true }
  ]} columns={[{ header: 'Código', render: (item) => item.codigo }, { header: 'Nome', render: (item) => item.nome }, { header: 'Carga horária', render: (item) => `${item.cargaHoraria}h` }, { header: 'Situação', render: (item) => item.ativa ? 'Ativa' : 'Inativa' }]} />;
}
