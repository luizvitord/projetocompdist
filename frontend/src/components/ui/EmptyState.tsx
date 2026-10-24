import { Button } from './Button';

export function EmptyState({ title, description, actionLabel, onAction }: { title: string; description: string; actionLabel?: string; onAction?: () => void }) {
  return <div className="rounded-xl border border-dashed border-slate-300 bg-white p-10 text-center">
    <h2 className="font-semibold text-slate-800">{title}</h2>
    <p className="mt-2 text-sm text-slate-600">{description}</p>
    {actionLabel && onAction ? <Button className="mt-4" onClick={onAction}>{actionLabel}</Button> : null}
  </div>;
}
