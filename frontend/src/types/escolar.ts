import type { IsoDate, IsoInstant } from './api';

export type MatriculaStatus = 'ATIVA' | 'TRANCADA' | 'TRANSFERIDA' | 'CONCLUIDA' | 'CANCELADA' | string;
export type Matricula = { id: number; alunoId: number; turmaId: number; anoLetivo: number; status: MatriculaStatus; dataMatricula: IsoDate; dataEncerramento?: IsoDate | null; motivo?: string | null; matriculaOrigemId?: number | null; createdAt?: IsoInstant; updatedAt?: IsoInstant };
export type Avaliacao = { id: number; turmaId: number; disciplinaId: number; bimestre: number; tipo: string; descricao: string; peso: number; notaMaxima: number; data: IsoDate; createdAt?: IsoInstant; updatedAt?: IsoInstant };
export type Nota = { id: number; avaliacaoId: number; matriculaId: number; valor: number; createdAt?: IsoInstant; updatedAt?: IsoInstant };
export type StatusPresenca = 'PRESENTE' | 'FALTA' | 'ATRASO' | 'JUSTIFICADA' | string;
export type RegistroChamada = { matriculaId: number; status: StatusPresenca; observacao?: string | null };
export type Chamada = { id: number; turmaId: number; disciplinaId: number; data: IsoDate; professorId?: number | null; registros: RegistroChamada[]; createdAt?: IsoInstant; updatedAt?: IsoInstant };
