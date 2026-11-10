import type { IsoInstant } from './api';

export type Perfil = 'ADMIN' | 'PROFESSOR' | 'ALUNO' | 'RESPONSAVEL';

export type Usuario = {
  id: number;
  nome: string;
  email: string;
  perfil: Perfil;
  referenciaId: number | null;
};

export type Sessao = {
  token: string;
  expiraEm: IsoInstant;
  usuario: Usuario;
};

export const perfilLabels: Record<Perfil, string> = {
  ADMIN: 'Coordenação',
  PROFESSOR: 'Professor(a)',
  ALUNO: 'Aluno(a)',
  RESPONSAVEL: 'Responsável'
};
