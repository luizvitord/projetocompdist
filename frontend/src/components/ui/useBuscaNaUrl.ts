import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * Busca e página de uma listagem guardadas na URL (?busca=...&pagina=...), para que recarregar a tela
 * ou voltar no navegador mantenha o filtro. `pagina` é contada a partir de 1 na URL e de 0 aqui (como
 * na API). Mudar a busca volta para a página 1.
 */
export function useBuscaNaUrl() {
  const [params, setParams] = useSearchParams();
  const busca = params.get('busca') ?? '';
  const paginaNaUrl = Number(params.get('pagina'));
  const pagina = Number.isInteger(paginaNaUrl) && paginaNaUrl > 1 ? paginaNaUrl - 1 : 0;

  // A busca substitui a entrada do histórico (digitar não cria uma entrada por palavra)
  const setBusca = useCallback((texto: string) => setParams((atuais) => {
    const novos = new URLSearchParams(atuais);
    if (texto) novos.set('busca', texto); else novos.delete('busca');
    novos.delete('pagina');
    return novos;
  }, { replace: true }), [setParams]);

  const setPagina = useCallback((numero: number) => setParams((atuais) => {
    const novos = new URLSearchParams(atuais);
    if (numero > 0) novos.set('pagina', String(numero + 1)); else novos.delete('pagina');
    return novos;
  }), [setParams]);

  return { busca, pagina, setBusca, setPagina };
}
