import type { IsoDate, IsoInstant } from './api';

export type Aluno = { id: number; matricula: string; nome: string; dataNascimento: IsoDate; endereco: string; telefone: string; email: string; createdAt?: IsoInstant; updatedAt?: IsoInstant };
export type AlunoRequest = Omit<Aluno, 'id' | 'matricula' | 'createdAt' | 'updatedAt'>;
