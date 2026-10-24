import { Link } from 'react-router-dom';
import { RegistryPage } from '../administrativo/RegistryPage';
import type { Aluno } from '../../types/aluno';

export function AlunosPage() {
  return <RegistryPage<Aluno> title="Alunos" singular="aluno" path="/alunos" fields={[
    { name: 'nome', label: 'Nome', required: true }, { name: 'dataNascimento', label: 'Nascimento', type: 'date', required: true },
    { name: 'endereco', label: 'Endereço', required: true }, { name: 'telefone', label: 'Telefone', required: true }, { name: 'email', label: 'E-mail', type: 'email', required: true }
  ]} columns={[
    { header: 'Matrícula', render: (aluno) => aluno.matricula }, { header: 'Nome', render: (aluno) => <Link className="font-semibold text-teal-700 hover:underline" to={`/alunos/${aluno.id}`}>{aluno.nome}</Link> },
    { header: 'E-mail', render: (aluno) => aluno.email }, { header: 'Telefone', render: (aluno) => aluno.telefone }
  ]} />;
}
