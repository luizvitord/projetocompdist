import { useEffect, useRef, useState } from 'react';
import { api } from '../../services/http';
import type { Turma } from '../../types/turma';

/**
 * Carrega só as turmas citadas na página exibida (GET /turmas/{id}), em vez de uma página fixa de
 * turmas que deixaria de fora as que não estão entre as primeiras. Cada id é buscado uma vez.
 * O valor é `null` quando a turma não pôde ser carregada e `undefined` enquanto carrega.
 */
export function useTurmasPorId(ids: readonly number[]) {
  const [turmas, setTurmas] = useState<Record<number, Turma | null>>({});
  const solicitadas = useRef(new Set<number>());
  const chave = [...new Set(ids)].sort((a, b) => a - b).join(',');

  useEffect(() => {
    if (!chave) return;
    chave.split(',').map(Number).filter((id) => !solicitadas.current.has(id)).forEach((id) => {
      solicitadas.current.add(id);
      api.get<Turma>(`/turmas/${id}`)
        .then((turma) => setTurmas((atual) => ({ ...atual, [id]: turma })))
        .catch(() => setTurmas((atual) => ({ ...atual, [id]: null })));
    });
  }, [chave]);

  return turmas;
}

/** "9A-2026 — 9º Ano A", "Turma #12" se não carregou, "Carregando..." enquanto carrega. */
export function rotuloTurma(turmas: Record<number, Turma | null>, id: number) {
  const turma = turmas[id];
  if (turma === undefined) return 'Carregando...';
  return turma ? `${turma.codigo} — ${turma.nome}` : `Turma #${id}`;
}
