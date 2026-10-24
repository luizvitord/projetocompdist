import type { IsoInstant } from './api';

export type Turno = 'MATUTINO' | 'VESPERTINO' | 'NOTURNO' | string;
export type ProfessorResumo = { id: number; nome: string };
export type DisciplinaOfertada = { disciplinaId: number; codigo: string; nome: string; professorId: number | null; professorNome: string | null };
export type Turma = { id: number; codigo: string; nome: string; anoLetivo: number; turno: Turno; capacidade: number; professores: ProfessorResumo[]; disciplinas: DisciplinaOfertada[]; createdAt?: IsoInstant; updatedAt?: IsoInstant };
export type TurmaRequest = Pick<Turma, 'codigo' | 'nome' | 'anoLetivo' | 'turno' | 'capacidade'>;
