import * as RadixDialog from '@radix-ui/react-dialog';
import type { PropsWithChildren, ReactNode } from 'react';

export function Dialog({ trigger, title, children }: PropsWithChildren<{ trigger: ReactNode; title: string }>) {
  return <RadixDialog.Root>
    <RadixDialog.Trigger asChild>{trigger}</RadixDialog.Trigger>
    <RadixDialog.Portal>
      <RadixDialog.Overlay className="fixed inset-0 bg-slate-950/40" />
      <RadixDialog.Content className="fixed left-1/2 top-1/2 w-[min(92vw,42rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl">
        <RadixDialog.Title className="text-lg font-bold text-slate-900">{title}</RadixDialog.Title>
        <div className="mt-4">{children}</div>
        <RadixDialog.Close className="absolute right-4 top-4 text-slate-500" aria-label="Fechar">×</RadixDialog.Close>
      </RadixDialog.Content>
    </RadixDialog.Portal>
  </RadixDialog.Root>;
}
