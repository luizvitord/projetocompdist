import { useState } from 'react';
import { Button } from '../../components/ui/Button';
import { RegistryPage } from '../administrativo/RegistryPage';
import type { Professor, Tecnico } from '../../types/administrativo';

export function PessoasPage() {
  const [tab, setTab] = useState<'professores' | 'tecnicos'>('professores');
  return <section className="space-y-4"><div className="flex gap-2" role="tablist"><Button variant={tab === 'professores' ? 'primary' : 'secondary'} onClick={() => setTab('professores')}>Professores</Button><Button variant={tab === 'tecnicos' ? 'primary' : 'secondary'} onClick={() => setTab('tecnicos')}>Técnicos</Button></div>
    {tab === 'professores' ? <RegistryPage<Professor> title="Professores" singular="professor" path="/professores" fields={[{ name: 'nome', label: 'Nome', required: true }, { name: 'qualificacao', label: 'Qualificação', required: true }, { name: 'email', label: 'E-mail', type: 'email', required: true }, { name: 'telefone', label: 'Telefone', required: true }]} columns={[{ header: 'Nome', render: (item) => item.nome }, { header: 'Qualificação', render: (item) => item.qualificacao }, { header: 'Contato', render: (item) => item.email }]} /> :
      <RegistryPage<Tecnico> title="Técnicos" singular="técnico" path="/tecnicos" toPayload={(form) => ({ nome: form.get('nome'), cpf: form.get('cpf'), cargo: form.get('cargo'), setor: form.get('setor'), email: form.get('email'), telefone: form.get('telefone'), dataAdmissao: form.get('dataAdmissao'), ativo: form.get('ativo') === 'on' })} fields={[{ name: 'nome', label: 'Nome', required: true }, { name: 'cpf', label: 'CPF', required: true }, { name: 'cargo', label: 'Cargo', required: true }, { name: 'setor', label: 'Setor', required: true }, { name: 'email', label: 'E-mail', type: 'email', required: true }, { name: 'telefone', label: 'Telefone', required: true }, { name: 'dataAdmissao', label: 'Admissão', type: 'date', required: true }, { name: 'ativo', label: 'Ativo', type: 'checkbox', defaultValue: true }]} columns={[{ header: 'Nome', render: (item) => item.nome }, { header: 'Cargo', render: (item) => item.cargo }, { header: 'Setor', render: (item) => item.setor }, { header: 'Situação', render: (item) => item.ativo ? 'Ativo' : 'Inativo' }]} />}
  </section>;
}
