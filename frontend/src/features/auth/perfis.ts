import type { Perfil, Usuario } from '../../types/auth';

/**
 * ALUNO e RESPONSAVEL usam o portal do aluno: o gateway só deixa esses perfis lerem os dados do
 * aluno ligado à conta (referenciaId), então as telas da coordenação não servem para eles.
 */
export function usaPortal(perfil: Perfil): boolean {
  return perfil === 'ALUNO' || perfil === 'RESPONSAVEL';
}

/** Para onde o usuário vai depois do login (e quando abre uma rota que não é do perfil dele). */
export function paginaInicial(usuario: Usuario): string {
  return usaPortal(usuario.perfil) ? '/portal' : '/dashboard';
}
