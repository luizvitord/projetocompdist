import type { IsoInstant } from './api';

export type TipoNotificacao =
  | 'NOTA_LANCADA'
  | 'FALTA_REGISTRADA'
  | 'MATRICULA_CRIADA'
  | 'MATRICULA_ALTERADA'
  | 'ALUNO_CADASTRADO'
  | 'PROFESSOR_ATRIBUIDO'
  | 'TECNICO_CADASTRADO';

/** Mesmo formato na API REST e nas mensagens do WebSocket. */
export type Notificacao = {
  id: number;
  tipo: TipoNotificacao;
  titulo: string;
  mensagem: string;
  /** Rota do portal relacionada à notificação (ex.: /alunos/12). */
  link: string | null;
  criadaEm: IsoInstant;
  lidaEm: IsoInstant | null;
  lida: boolean;
};
