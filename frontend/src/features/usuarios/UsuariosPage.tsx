import { FormEvent, useEffect, useState } from 'react';
import { useFeedback } from '../../components/feedback/FeedbackProvider';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { DataTable } from '../../components/ui/DataTable';
import { Dialog } from '../../components/ui/Dialog';
import { EmptyState } from '../../components/ui/EmptyState';
import { Pagination } from '../../components/ui/Pagination';
import { api, isApiProblem } from '../../services/http';
import { toPage, type Page, type SpringPagedModel } from '../../services/pagination';
import { getSession } from '../../services/session';
import type { Perfil, Usuario } from '../../types/auth';
import { perfilLabels } from '../../types/auth';

type UsuarioAdministrativo = Usuario & { ativo: boolean };
type CadastroUsuario = { nome: string; email: string; senha: string; perfil: Perfil; referenciaId: number | null };
type AtualizacaoUsuario = Omit<CadastroUsuario, 'senha'>;

const perfis: Perfil[] = ['ADMIN', 'PROFESSOR', 'ALUNO', 'RESPONSAVEL'];

function rotuloReferencia(usuario: UsuarioAdministrativo) {
  if (usuario.referenciaId === null) return 'Sem vínculo';
  if (usuario.perfil === 'PROFESSOR') return `Professor #${usuario.referenciaId}`;
  return `Aluno #${usuario.referenciaId}`;
}

function rotuloCampoReferencia(perfil: Perfil) {
  return perfil === 'PROFESSOR' ? 'ID do professor vinculado' : 'ID do aluno vinculado';
}

function FormularioUsuario({ usuario, submitting, onSubmit, erros }: {
  usuario?: UsuarioAdministrativo;
  submitting: boolean;
  erros: Record<string, string>;
  onSubmit: (payload: CadastroUsuario | AtualizacaoUsuario) => Promise<void>;
}) {
  const editando = Boolean(usuario);
  const [perfil, setPerfil] = useState<Perfil>(usuario?.perfil ?? 'ADMIN');
  const [referenciaId, setReferenciaId] = useState(usuario?.referenciaId?.toString() ?? '');
  const usaReferencia = perfil !== 'ADMIN';

  async function enviar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const referencia = Number(referenciaId);
    const base = {
      nome: String(form.get('nome') ?? '').trim(),
      email: String(form.get('email') ?? '').trim(),
      perfil,
      referenciaId: perfil === 'ADMIN' ? null : referencia
    };
    if (editando) await onSubmit(base);
    else await onSubmit({ ...base, senha: String(form.get('senha') ?? '') });
  }

  return <form className="grid gap-3 sm:grid-cols-2" onSubmit={(event) => void enviar(event)}>
    <label className="text-sm font-medium text-slate-700">Nome
      <input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" name="nome" required defaultValue={usuario?.nome ?? ''} disabled={submitting} />
      {erros.nome ? <span className="mt-1 block text-xs text-rose-700">{erros.nome}</span> : null}
    </label>
    <label className="text-sm font-medium text-slate-700">E-mail
      <input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" name="email" type="email" required defaultValue={usuario?.email ?? ''} disabled={submitting} />
      {erros.email ? <span className="mt-1 block text-xs text-rose-700">{erros.email}</span> : null}
    </label>
    {!editando ? <label className="text-sm font-medium text-slate-700">Senha
      <input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" name="senha" type="password" minLength={6} maxLength={72} required disabled={submitting} />
      {erros.senha ? <span className="mt-1 block text-xs text-rose-700">{erros.senha}</span> : null}
    </label> : <p className="self-end text-xs text-slate-500">Use a ação “Alterar senha” para redefinir o acesso.</p>}
    <label className="text-sm font-medium text-slate-700">Perfil
      <select className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" name="perfil" value={perfil} onChange={(event) => { setPerfil(event.target.value as Perfil); setReferenciaId(''); }} disabled={submitting}>
        {perfis.map((opcao) => <option key={opcao} value={opcao}>{perfilLabels[opcao]}</option>)}
      </select>
      {erros.perfil ? <span className="mt-1 block text-xs text-rose-700">{erros.perfil}</span> : null}
    </label>
    {usaReferencia ? <label className="text-sm font-medium text-slate-700">{rotuloCampoReferencia(perfil)}
      <input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" name="referenciaId" type="number" min="1" step="1" required value={referenciaId} onChange={(event) => setReferenciaId(event.target.value)} disabled={submitting} />
      <span className="mt-1 block text-xs font-normal text-slate-500">Use o identificador do registro já cadastrado.</span>
      {erros.referenciaId ? <span className="mt-1 block text-xs text-rose-700">{erros.referenciaId}</span> : null}
    </label> : <p className="self-end text-xs text-slate-500">Contas da coordenação não têm vínculo com aluno ou professor.</p>}
    {erros.form ? <p role="alert" className="text-sm text-rose-700 sm:col-span-2">{erros.form}</p> : null}
    <Button className="sm:col-span-2" type="submit" disabled={submitting}>{submitting ? 'Salvando...' : editando ? 'Salvar alterações' : 'Cadastrar usuário'}</Button>
  </form>;
}

export function UsuariosPage() {
  const [pagina, setPagina] = useState<Page<UsuarioAdministrativo> | null>(null);
  const [numeroPagina, setNumeroPagina] = useState(0);
  const [perfilFiltro, setPerfilFiltro] = useState<Perfil | ''>('');
  const [recarregar, setRecarregar] = useState(0);
  const [erroLista, setErroLista] = useState('');
  const [errosFormulario, setErrosFormulario] = useState<Record<string, string>>({});
  const [criacaoAberta, setCriacaoAberta] = useState(false);
  const [edicao, setEdicao] = useState<UsuarioAdministrativo | null>(null);
  const [redefinicaoSenha, setRedefinicaoSenha] = useState<UsuarioAdministrativo | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [erroRedefinicao, setErroRedefinicao] = useState('');
  const { showError, showSuccess } = useFeedback();
  const idUsuarioAtual = getSession()?.usuario.id;

  const atualizar = () => setRecarregar((atual) => atual + 1);
  useEffect(() => {
    setErroLista('');
    api.get<SpringPagedModel<UsuarioAdministrativo>>('/usuarios', { query: { perfil: perfilFiltro || undefined, ativo: true, page: numeroPagina, size: 20 } })
      .then((dados) => setPagina(toPage(dados)))
      .catch(() => setErroLista('Não foi possível carregar os usuários.'));
  }, [numeroPagina, perfilFiltro, recarregar]);

  function falhaFormulario(erro: unknown) {
    setErrosFormulario(isApiProblem(erro) ? erro.fieldErrors : { form: 'Não foi possível salvar os dados.' });
  }

  async function cadastrar(payload: CadastroUsuario | AtualizacaoUsuario) {
    if (!('senha' in payload)) return;
    setEnviando(true);
    try {
      await api.post('/usuarios', payload);
      setCriacaoAberta(false); setErrosFormulario({}); showSuccess('Usuário cadastrado com sucesso.'); atualizar();
    } catch (erro) { falhaFormulario(erro); } finally { setEnviando(false); }
  }

  async function editar(payload: CadastroUsuario | AtualizacaoUsuario) {
    if (!edicao || 'senha' in payload) return;
    setEnviando(true);
    try {
      await api.put(`/usuarios/${edicao.id}`, payload);
      setEdicao(null); setErrosFormulario({}); showSuccess('Usuário atualizado com sucesso.'); atualizar();
    } catch (erro) { falhaFormulario(erro); } finally { setEnviando(false); }
  }

  async function desativar(usuario: UsuarioAdministrativo) {
    try {
      await api.delete(`/usuarios/${usuario.id}`);
      showSuccess('Usuário desativado com sucesso.'); atualizar();
    } catch (erro) { showError(isApiProblem(erro) ? erro.detail : 'Não foi possível desativar o usuário.'); }
  }

  async function redefinirSenha(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!redefinicaoSenha) return;
    const senha = String(new FormData(event.currentTarget).get('senha') ?? '');
    setEnviando(true); setErroRedefinicao('');
    try {
      await api.patch(`/usuarios/${redefinicaoSenha.id}/senha`, { senha });
      setRedefinicaoSenha(null); showSuccess('Senha redefinida com sucesso.');
    } catch (erro) {
      setErroRedefinicao(isApiProblem(erro) ? erro.detail : 'Não foi possível redefinir a senha.');
    } finally { setEnviando(false); }
  }

  return <section className="space-y-5">
    <header className="flex flex-wrap items-end justify-between gap-3"><div><h1 className="text-2xl font-bold text-navy">Usuários</h1><p className="mt-1 text-sm text-slate-600">Gerencie acessos e vínculos dos perfis da escola.</p></div>
      <Dialog title="Novo usuário" open={criacaoAberta} onOpenChange={(aberta) => { setCriacaoAberta(aberta); if (aberta) setErrosFormulario({}); }} trigger={<Button>Novo usuário</Button>}>
        {criacaoAberta ? <FormularioUsuario key="novo" submitting={enviando} erros={errosFormulario} onSubmit={cadastrar} /> : null}
      </Dialog>
    </header>
    <label className="block max-w-xs text-sm font-medium text-slate-700">Filtrar por perfil
      <select className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" value={perfilFiltro} onChange={(event) => { setPerfilFiltro(event.target.value as Perfil | ''); setNumeroPagina(0); }}>
        <option value="">Todos os perfis</option>
        {perfis.map((perfil) => <option key={perfil} value={perfil}>{perfilLabels[perfil]}</option>)}
      </select>
    </label>
    {erroLista ? <div role="alert" className="rounded-lg bg-rose-50 p-4 text-rose-800"><p>{erroLista}</p><Button className="mt-3" type="button" variant="secondary" onClick={atualizar}>Tentar novamente</Button></div> : !pagina ? <p role="status" className="rounded-lg bg-white p-4 text-slate-600">Carregando usuários...</p> : pagina.content.length ? <><DataTable items={pagina.content} keyFor={(usuario) => usuario.id} columns={[
      { header: 'Nome', render: (usuario) => usuario.nome }, { header: 'E-mail', render: (usuario) => usuario.email }, { header: 'Perfil', render: (usuario) => perfilLabels[usuario.perfil] }, { header: 'Vínculo', render: rotuloReferencia },
      { header: 'Ações', render: (usuario) => <div className="flex flex-wrap gap-2"><Button type="button" variant="secondary" onClick={() => { setErrosFormulario({}); setEdicao(usuario); }}>Editar</Button><Button type="button" variant="secondary" onClick={() => { setErroRedefinicao(''); setRedefinicaoSenha(usuario); }}>Alterar senha</Button>{usuario.id !== idUsuarioAtual ? <ConfirmDialog title="Desativar usuário" description="A conta não poderá mais entrar no sistema, mas o histórico será mantido." confirmLabel="Desativar" trigger={<Button type="button" variant="secondary">Desativar</Button>} onConfirm={() => desativar(usuario)} /> : null}</div> }
    ]} /><Pagination page={pagina.number} totalPages={pagina.totalPages} onPageChange={setNumeroPagina} /></> : <EmptyState title="Nenhum usuário encontrado" description="Cadastre a primeira conta para começar." />}
    <Dialog title="Editar usuário" open={Boolean(edicao)} onOpenChange={(aberta) => { if (!aberta) { setEdicao(null); setErrosFormulario({}); } }} trigger={<span />}>
      {edicao ? <FormularioUsuario key={edicao.id} usuario={edicao} submitting={enviando} erros={errosFormulario} onSubmit={editar} /> : null}
    </Dialog>
    <Dialog title="Redefinir senha" open={Boolean(redefinicaoSenha)} onOpenChange={(aberta) => { if (!aberta) { setRedefinicaoSenha(null); setErroRedefinicao(''); } }} trigger={<span />}>
      {redefinicaoSenha ? <form className="space-y-3" onSubmit={(event) => void redefinirSenha(event)}>
        <p className="text-sm text-slate-600">Defina uma nova senha para {redefinicaoSenha.nome}.</p>
        <label className="block text-sm font-medium text-slate-700">Nova senha
          <input className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" name="senha" type="password" minLength={6} maxLength={72} required disabled={enviando} />
        </label>
        {erroRedefinicao ? <p role="alert" className="text-sm text-rose-700">{erroRedefinicao}</p> : null}
        <Button type="submit" disabled={enviando}>{enviando ? 'Redefinindo...' : 'Redefinir senha'}</Button>
      </form> : null}
    </Dialog>
  </section>;
}
