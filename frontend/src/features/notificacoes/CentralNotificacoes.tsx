import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useFeedback } from '../../components/feedback/FeedbackProvider';
import type { Notificacao } from '../../types/notificacao';
import { useNotificacoes } from './useNotificacoes';

const dataHora = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

function rotuloDoSino(naoLidas: number) {
  if (naoLidas === 0) return 'Notificações';
  return naoLidas === 1 ? 'Notificações: 1 não lida' : `Notificações: ${naoLidas} não lidas`;
}

/** Sino do topo com a contagem de não lidas e o painel das últimas notificações. */
export function CentralNotificacoes() {
  const { naoLidas, itens, carregando, erro, carregar, marcarLida, marcarTodasLidas } = useNotificacoes();
  const { showError } = useFeedback();
  const [aberto, setAberto] = useState(false);
  const container = useRef<HTMLDivElement>(null);

  // Fecha ao clicar fora ou apertar Esc
  useEffect(() => {
    if (!aberto) return;
    const aoClicar = (event: MouseEvent) => {
      if (!container.current?.contains(event.target as Node)) setAberto(false);
    };
    const aoTeclar = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setAberto(false);
    };
    document.addEventListener('mousedown', aoClicar);
    document.addEventListener('keydown', aoTeclar);
    return () => {
      document.removeEventListener('mousedown', aoClicar);
      document.removeEventListener('keydown', aoTeclar);
    };
  }, [aberto]);

  function alternar() {
    if (!aberto) void carregar();
    setAberto(!aberto);
  }

  async function lida(notificacao: Notificacao) {
    if (notificacao.lida) return;
    try {
      await marcarLida(notificacao.id);
    } catch {
      showError('Não foi possível marcar a notificação como lida.');
    }
  }

  async function todasLidas() {
    try {
      await marcarTodasLidas();
    } catch {
      showError('Não foi possível marcar as notificações como lidas.');
    }
  }

  return <div className="relative" ref={container}>
    <button type="button" onClick={alternar} aria-label={rotuloDoSino(naoLidas)} aria-haspopup="dialog" aria-expanded={aberto}
      className="relative rounded-lg p-2 text-slate-600 transition hover:bg-slate-100">
      <svg aria-hidden="true" viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M6 8a6 6 0 1 1 12 0c0 7 3 8 3 8H3s3-1 3-8" /><path d="M10 20a2 2 0 0 0 4 0" />
      </svg>
      {naoLidas > 0 ? <span data-testid="contagem-nao-lidas" className="absolute -right-0.5 -top-0.5 min-w-5 rounded-full bg-rose-600 px-1 text-center text-xs font-bold leading-5 text-white">{naoLidas > 99 ? '99+' : naoLidas}</span> : null}
    </button>

    {aberto ? <section role="dialog" aria-label="Notificações" className="absolute right-0 z-40 mt-2 w-[min(24rem,calc(100vw-2rem))] rounded-xl border border-slate-200 bg-white shadow-xl">
      <header className="flex items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
        <h2 className="text-sm font-bold text-navy">Notificações</h2>
        <button type="button" onClick={todasLidas} disabled={naoLidas === 0} className="text-xs font-semibold text-teal-700 hover:underline disabled:cursor-default disabled:text-slate-400 disabled:no-underline">Marcar todas como lidas</button>
      </header>
      {erro ? <p role="alert" className="p-4 text-sm text-rose-800">{erro} <button type="button" onClick={() => void carregar()} className="font-semibold underline">Tentar novamente</button></p> : null}
      {!erro && itens === null && carregando ? <p className="p-4 text-sm text-slate-500">Carregando notificações...</p> : null}
      {!erro && itens?.length === 0 ? <p className="p-4 text-sm text-slate-500">Nenhuma notificação por enquanto.</p> : null}
      {!erro && itens && itens.length > 0 ? <ul className="max-h-96 divide-y divide-slate-100 overflow-y-auto">
        {itens.map((notificacao) => <li key={notificacao.id} className={`px-4 py-3 ${notificacao.lida ? '' : 'bg-teal-50/60'}`}>
          <div className="flex items-start justify-between gap-3">
            <p className="text-sm font-semibold text-slate-800">{notificacao.titulo}{notificacao.lida ? null : <span className="sr-only"> (não lida)</span>}</p>
            <time dateTime={notificacao.criadaEm} className="shrink-0 text-xs text-slate-500">{dataHora.format(new Date(notificacao.criadaEm))}</time>
          </div>
          <p className="mt-1 text-sm text-slate-600">{notificacao.mensagem}</p>
          <div className="mt-2 flex items-center gap-4 text-xs font-semibold">
            {notificacao.link ? <Link to={notificacao.link} onClick={() => { void lida(notificacao); setAberto(false); }} className="text-teal-700 hover:underline">Abrir</Link> : null}
            {notificacao.lida ? null : <button type="button" onClick={() => void lida(notificacao)} className="text-slate-600 hover:underline">Marcar como lida</button>}
          </div>
        </li>)}
      </ul> : null}
    </section> : null}
  </div>;
}
