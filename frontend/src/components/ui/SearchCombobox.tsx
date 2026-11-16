import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';

/** Espera depois da última tecla antes de consultar a API. */
export const SEARCH_DEBOUNCE_MS = 300;

type SearchState<T> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'ready'; items: T[] }
  | { status: 'error' };

export type SearchComboboxProps<T> = {
  label: string;
  /** Busca no servidor; recebe o texto digitado (sem espaços nas pontas) e um sinal de cancelamento. */
  search: (texto: string, signal: AbortSignal) => Promise<T[]>;
  getKey: (item: T) => string | number;
  getLabel: (item: T) => string;
  /** Valor enviado no formulário (campo oculto `name`). */
  getValue?: (item: T) => string;
  name?: string;
  required?: boolean;
  disabled?: boolean;
  placeholder?: string;
  /** Item já selecionado ao montar (ex.: edição). */
  defaultSelected?: T | null;
  onSelect?: (item: T | null) => void;
  /** Quantidade mínima de caracteres para buscar; 0 lista os primeiros itens ao abrir. */
  minLength?: number;
  emptyMessage?: string;
  /** Texto de ajuda exibido abaixo do campo. */
  hint?: string;
};

/**
 * Campo de busca com lista de sugestões (padrão combobox do WAI-ARIA 1.2). Consulta o servidor a
 * cada pausa na digitação, em vez de carregar todas as opções num <select>, e por isso funciona
 * com qualquer volume de registros.
 */
export function SearchCombobox<T>({ label, search, getKey, getLabel, getValue, name, required, disabled, placeholder, defaultSelected = null, onSelect, minLength = 1, emptyMessage = 'Nenhum resultado encontrado.', hint }: SearchComboboxProps<T>) {
  const id = useId();
  const inputId = `${id}-input`;
  const listboxId = `${id}-listbox`;
  const statusId = `${id}-status`;
  const hintId = `${id}-hint`;
  const inputRef = useRef<HTMLInputElement>(null);
  const searchRef = useRef(search);
  useEffect(() => { searchRef.current = search; });

  const [selected, setSelected] = useState<T | null>(defaultSelected);
  const [texto, setTexto] = useState(defaultSelected ? getLabel(defaultSelected) : '');
  const [open, setOpen] = useState(false);
  const [state, setState] = useState<SearchState<T>>({ status: 'idle' });
  const [activeIndex, setActiveIndex] = useState(-1);

  // Enquanto a lista está aberta e nada foi escolhido, consulta o servidor após a pausa na digitação
  const termo = texto.trim();
  const buscar = open && !selected && termo.length >= minLength;
  useEffect(() => {
    if (!buscar) { setState({ status: 'idle' }); return; }
    const controller = new AbortController();
    setState({ status: 'loading' });
    const timer = window.setTimeout(() => {
      searchRef.current(termo, controller.signal)
        .then((items) => { if (!controller.signal.aborted) { setState({ status: 'ready', items }); setActiveIndex(-1); } })
        .catch(() => { if (!controller.signal.aborted) setState({ status: 'error' }); });
    }, SEARCH_DEBOUNCE_MS);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [buscar, termo]);

  // Texto digitado sem escolher um item da lista não é um valor válido
  useEffect(() => {
    inputRef.current?.setCustomValidity(termo && !selected ? 'Selecione uma opção da lista.' : '');
  }, [termo, selected]);

  const items = state.status === 'ready' ? state.items : [];
  const expanded = open && items.length > 0;
  const optionId = (index: number) => `${id}-option-${index}`;

  function escolher(item: T) {
    setSelected(item); setTexto(getLabel(item)); setOpen(false); setActiveIndex(-1);
    onSelect?.(item);
  }
  function alterarTexto(valor: string) {
    setTexto(valor); setOpen(true);
    if (selected) { setSelected(null); onSelect?.(null); }
  }
  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (!open) { setOpen(true); return; }
      if (items.length) setActiveIndex((atual) => (atual + 1) % items.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (items.length) setActiveIndex((atual) => (atual <= 0 ? items.length - 1 : atual - 1));
    } else if (event.key === 'Enter') {
      // Com a lista aberta, Enter escolhe a opção em vez de enviar o formulário
      if (open && (activeIndex >= 0 || items.length)) event.preventDefault();
      if (open && activeIndex >= 0 && items[activeIndex]) escolher(items[activeIndex]);
    } else if (event.key === 'Escape' && open) {
      event.preventDefault();
      setOpen(false); setActiveIndex(-1);
    }
  }

  let statusMessage = '';
  if (open && !selected) {
    if (termo.length < minLength) statusMessage = `Digite ao menos ${minLength} ${minLength === 1 ? 'caractere' : 'caracteres'} para buscar.`;
    else if (state.status === 'loading') statusMessage = 'Buscando...';
    else if (state.status === 'error') statusMessage = 'Não foi possível buscar. Tente novamente.';
    else if (state.status === 'ready' && !items.length) statusMessage = emptyMessage;
    else if (state.status === 'ready') statusMessage = `${items.length} ${items.length === 1 ? 'resultado' : 'resultados'}.`;
  }
  const mostrarStatusVisivel = statusMessage && !expanded;

  return <div className="relative">
    <label htmlFor={inputId}>{label}</label>
    <input
      ref={inputRef}
      id={inputId}
      type="text"
      role="combobox"
      autoComplete="off"
      aria-autocomplete="list"
      aria-expanded={expanded}
      aria-controls={listboxId}
      aria-activedescendant={expanded && activeIndex >= 0 ? optionId(activeIndex) : undefined}
      aria-describedby={[hint ? hintId : '', statusId].filter(Boolean).join(' ')}
      required={required}
      disabled={disabled}
      placeholder={placeholder}
      value={texto}
      onChange={(event) => alterarTexto(event.target.value)}
      onFocus={() => { if (!selected) setOpen(true); }}
      onClick={() => { if (!selected) setOpen(true); }}
      onBlur={() => { setOpen(false); setActiveIndex(-1); }}
      onKeyDown={onKeyDown}
      className="mt-1 w-full rounded border p-2"
    />
    {name ? <input type="hidden" name={name} value={selected ? (getValue ?? ((item: T) => String(getKey(item))))(selected) : ''} /> : null}
    {hint ? <p id={hintId} className="mt-1 text-xs text-slate-500">{hint}</p> : null}
    <ul id={listboxId} role="listbox" aria-label={label} hidden={!expanded} className="absolute left-0 right-0 z-10 mt-1 max-h-60 overflow-auto rounded border bg-white shadow-lg">
      {expanded ? items.map((item, index) => <li
        key={getKey(item)}
        id={optionId(index)}
        role="option"
        aria-selected={index === activeIndex}
        // mousedown tiraria o foco do campo e fecharia a lista antes do clique
        onMouseDown={(event) => event.preventDefault()}
        onClick={() => escolher(item)}
        onMouseEnter={() => setActiveIndex(index)}
        className={`cursor-pointer px-3 py-2 text-sm ${index === activeIndex ? 'bg-teal-50 text-teal-900' : 'text-slate-800'}`}
      >{getLabel(item)}</li>) : null}
    </ul>
    <p id={statusId} role="status" aria-live="polite" className={mostrarStatusVisivel ? 'mt-1 text-sm text-slate-600' : 'sr-only'}>{statusMessage}</p>
  </div>;
}
