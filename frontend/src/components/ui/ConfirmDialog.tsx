import * as RadixDialog from '@radix-ui/react-dialog';
import { useState, type ReactNode } from 'react';
import { Button } from './Button';

export function ConfirmDialog({ trigger, title, description, confirmLabel = 'Confirmar', onConfirm }: { trigger: ReactNode; title: string; description: string; confirmLabel?: string; onConfirm: () => Promise<void> | void }) {
  const [open, setOpen] = useState(false); const [loading, setLoading] = useState(false);
  async function confirm() { setLoading(true); try { await onConfirm(); setOpen(false); } finally { setLoading(false); } }
  return <RadixDialog.Root open={open} onOpenChange={setOpen}>
    <RadixDialog.Trigger asChild>{trigger}</RadixDialog.Trigger>
    <RadixDialog.Portal><RadixDialog.Overlay className="fixed inset-0 z-40 bg-slate-950/40" />
      <RadixDialog.Content className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,28rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl">
        <RadixDialog.Title className="text-lg font-bold text-slate-900">{title}</RadixDialog.Title>
        <RadixDialog.Description className="mt-2 text-sm text-slate-600">{description}</RadixDialog.Description>
        <div className="mt-5 flex justify-end gap-3"><RadixDialog.Close asChild><Button type="button" variant="secondary" disabled={loading}>Cancelar</Button></RadixDialog.Close><Button type="button" onClick={() => void confirm()} disabled={loading}>{loading ? 'Enviando...' : confirmLabel}</Button></div>
      </RadixDialog.Content>
    </RadixDialog.Portal>
  </RadixDialog.Root>;
}
