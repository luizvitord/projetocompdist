import { useCallback, useEffect, useRef, useState } from 'react';
import { useFeedback } from '../../components/feedback/FeedbackProvider';
import { getSession } from '../../services/session';
import type { Notificacao } from '../../types/notificacao';
import { contarNaoLidas, listarNotificacoes, marcarComoLida, marcarTodasComoLidas } from './notificacoesApi';
import { conectarNotificacoes } from './notificacoesSocket';

/** Quantas notificações o painel mostra. */
export const TAMANHO_DO_PAINEL = 10;

/** setTimeout aceita no máximo 2^31 - 1 ms (~24 dias). */
const ESPERA_MAXIMA_MS = 2_147_483_647;

type Estado = {
  naoLidas: number;
  /** null enquanto o painel ainda não foi aberto. */
  itens: Notificacao[] | null;
  carregando: boolean;
  erro: string | null;
};

/**
 * Estado da central de notificações: contagem de não lidas, últimas notificações e a conexão em
 * tempo real.
 *
 * A fonte da verdade é a API REST. O WebSocket só adianta o que chegou: a cada conexão (inclusive
 * reconexões) a contagem é buscada de novo, para não perder o que foi criado enquanto estava
 * desconectado. A conexão é fechada quando o componente sai da tela (logout) e quando o token
 * expira.
 */
export function useNotificacoes() {
  const { showInfo } = useFeedback();
  const [estado, setEstado] = useState<Estado>({ naoLidas: 0, itens: null, carregando: false, erro: null });
  /** O painel já foi aberto: novas conexões também atualizam a lista. */
  const listaCarregada = useRef(false);

  const atualizarContagem = useCallback(async () => {
    try {
      const total = await contarNaoLidas();
      setEstado((atual) => ({ ...atual, naoLidas: total }));
    } catch {
      // Sem contagem agora: o sino mantém o último valor e tenta de novo na próxima conexão
    }
  }, []);

  const carregar = useCallback(async () => {
    listaCarregada.current = true;
    setEstado((atual) => ({ ...atual, carregando: true, erro: null }));
    try {
      const pagina = await listarNotificacoes(TAMANHO_DO_PAINEL);
      setEstado((atual) => ({ ...atual, itens: pagina.content, carregando: false }));
    } catch {
      setEstado((atual) => ({ ...atual, carregando: false, erro: 'Não foi possível carregar as notificações.' }));
    }
  }, []);

  useEffect(() => {
    const socket = conectarNotificacoes({
      onConectar: () => {
        void atualizarContagem();
        if (listaCarregada.current) void carregar();
      },
      onNotificacao: (nova) => {
        setEstado((atual) => {
          // A mesma notificação pode chegar pelo WebSocket e pela lista recarregada
          if (atual.itens?.some((item) => item.id === nova.id)) return atual;
          return {
            ...atual,
            naoLidas: atual.naoLidas + 1,
            itens: atual.itens ? [nova, ...atual.itens].slice(0, TAMANHO_DO_PAINEL) : atual.itens
          };
        });
        showInfo(nova.mensagem);
      }
    });

    // Token expirado: o servidor só confere o token na conexão, então quem desconecta é o cliente
    const expiraEm = getSession()?.expiraEm;
    const restante = expiraEm ? new Date(expiraEm).getTime() - Date.now() : 0;
    const expiracao = setTimeout(() => socket.desconectar(), Math.min(Math.max(restante, 0), ESPERA_MAXIMA_MS));

    return () => {
      clearTimeout(expiracao);
      socket.desconectar();
    };
  }, [atualizarContagem, carregar, showInfo]);

  const marcarLida = useCallback(async (id: number) => {
    const lida = await marcarComoLida(id);
    setEstado((atual) => {
      const antes = atual.itens?.find((item) => item.id === id);
      return {
        ...atual,
        naoLidas: antes && !antes.lida ? Math.max(0, atual.naoLidas - 1) : atual.naoLidas,
        itens: atual.itens?.map((item) => (item.id === id ? lida : item)) ?? atual.itens
      };
    });
  }, []);

  const marcarTodasLidas = useCallback(async () => {
    await marcarTodasComoLidas();
    const agora = new Date().toISOString();
    setEstado((atual) => ({
      ...atual,
      naoLidas: 0,
      itens: atual.itens?.map((item) => (item.lida ? item : { ...item, lida: true, lidaEm: agora })) ?? atual.itens
    }));
  }, []);

  return { ...estado, carregar, marcarLida, marcarTodasLidas };
}
