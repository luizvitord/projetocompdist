import type { IsoDate, IsoInstant } from './api';

export type Disciplina = { id: number; codigo: string; nome: string; cargaHoraria: number; ativa: boolean; createdAt?: IsoInstant; updatedAt?: IsoInstant };
export type DisciplinaRequest = Omit<Disciplina, 'id' | 'createdAt' | 'updatedAt'>;
export type Professor = { id: number; nome: string; qualificacao: string; email: string; telefone: string; createdAt?: IsoInstant; updatedAt?: IsoInstant };
export type ProfessorRequest = Omit<Professor, 'id' | 'createdAt' | 'updatedAt'>;
export type Tecnico = { id: number; nome: string; cpf: string; cargo: string; setor: string; email: string; telefone: string; dataAdmissao: IsoDate; ativo: boolean; createdAt?: IsoInstant; updatedAt?: IsoInstant };
export type TecnicoRequest = Omit<Tecnico, 'id' | 'createdAt' | 'updatedAt'>;
