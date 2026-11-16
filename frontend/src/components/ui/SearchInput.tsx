import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';

/** Espera depois da última tecla antes de buscar. */
export const BUSCA_DEBOUNCE_MS = 300;
/** Mesmo limite da API (acima disso ela responde 400). */
export const BUSCA_TAMANHO_MAXIMO = 100;

type Props = {
  /** Busca aplicada (vinda da URL). Quando muda por fora, como ao voltar no navegador, o campo acompanha. */
  value: string;
  /** Recebe o texto sem espaços nas pontas; vazio = sem busca. */
  onSearch: (texto: string) => void;
  /** Diz o que é pesquisado na tela (ex.: "Buscar por nome, matrícula ou email"). */
  placeholder: string;
  /** Rótulo para leitores de tela (visualmente oculto). */
  label?: string;
};

/**
 * Campo de busca das listagens. Aplica a busca ~300 ms depois da última tecla, na hora com Enter, e
 * limpa com o botão × ou com Esc. Quem usa decide o que fazer com o texto (normalmente: guardar na
 * URL, voltar para a página 1 e consultar a API cancelando a requisição anterior).
 */
export function SearchInput({ value, onSearch, placeholder, label = 'Buscar' }: Props) {
  const id = useId();
  const [texto, setTexto] = useState(value);
  const onSearchRef = useRef(onSearch);
  useEffect(() => { onSearchRef.current = onSearch; });
  // Último valor aplicado; evita que a própria busca, ao voltar pela URL, sobrescreva o que foi digitado depois
  const aplicado = useRef(value);

  useEffect(() => {
    if (value !== aplicado.current) { aplicado.current = value; setTexto(value); }
  }, [value]);

  function aplicar(valor: string) {
    const termo = valor.trim();
    if (termo === aplicado.current) return;
    aplicado.current = termo;
    onSearchRef.current(termo);
  }

  useEffect(() => {
    const termo = texto.trim();
    if (termo === aplicado.current) return;
    const timer = window.setTimeout(() => { aplicado.current = termo; onSearchRef.current(termo); }, BUSCA_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [texto]);

  function limpar() { setTexto(''); aplicar(''); }
  function onSubmit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); aplicar(texto); }
  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Escape' && texto) { event.preventDefault(); limpar(); }
  }

  return <form role="search" aria-label={label} onSubmit={onSubmit} className="relative w-full max-w-xl">
    <label htmlFor={id} className="sr-only">{label}</label>
    <svg aria-hidden="true" viewBox="0 0 20 20" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="9" cy="9" r="6" /><path d="m14 14 4 4" strokeLinecap="round" /></svg>
    <input
      id={id}
      type="search"
      value={texto}
      maxLength={BUSCA_TAMANHO_MAXIMO}
      placeholder={placeholder}
      autoComplete="off"
      onChange={(event) => setTexto(event.target.value)}
      onKeyDown={onKeyDown}
      className="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-9 text-sm [&::-webkit-search-cancel-button]:hidden"
    />
    {texto ? <button type="button" aria-label="Limpar campo de busca" onClick={limpar} className="absolute right-2 top-1/2 -translate-y-1/2 rounded px-2 text-lg leading-none text-slate-500 hover:bg-slate-100 hover:text-slate-800">×</button> : null}
  </form>;
}
