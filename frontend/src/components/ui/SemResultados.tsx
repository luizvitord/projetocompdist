import { Button } from './Button';

/** Estado "a busca não encontrou nada", diferente de "nenhum registro cadastrado". */
export function SemResultados({ busca, onLimpar }: { busca: string; onLimpar: () => void }) {
  return <div role="status" className="rounded-xl border border-dashed border-slate-300 bg-white p-10 text-center">
    <h2 className="font-semibold text-slate-800">Nenhum resultado para "{busca}"</h2>
    <p className="mt-2 text-sm text-slate-600">Confira a grafia ou procure por outra parte do texto.</p>
    <Button className="mt-4" type="button" variant="secondary" onClick={onLimpar}>Limpar busca</Button>
  </div>;
}
