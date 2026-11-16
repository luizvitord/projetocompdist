import { Button } from './Button';

export function Pagination({ page, totalPages, totalElements, onPageChange }: { page: number; totalPages: number; totalElements?: number; onPageChange: (page: number) => void }) {
  return <nav aria-label="Paginação" className="flex items-center justify-between gap-3 text-sm text-slate-600">
    <Button variant="secondary" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>Anterior</Button>
    <span>Página {page + 1} de {Math.max(totalPages, 1)}{totalElements !== undefined ? ` · ${totalElements} ${totalElements === 1 ? 'registro' : 'registros'}` : ''}</span>
    <Button variant="secondary" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>Próxima</Button>
  </nav>;
}
