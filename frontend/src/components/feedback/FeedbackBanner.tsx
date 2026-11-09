export type FeedbackVariant = 'success' | 'error' | 'warning';

const styles: Record<FeedbackVariant, string> = {
  success: 'border-emerald-300 bg-emerald-50 text-emerald-900',
  error: 'border-rose-300 bg-rose-50 text-rose-900',
  warning: 'border-amber-300 bg-amber-50 text-amber-900'
};

export function FeedbackBanner({ message, variant, onClose }: { message: string; variant: FeedbackVariant; onClose: () => void }) {
  return <div className={`fixed right-5 top-5 z-50 flex max-w-md items-start gap-3 rounded-lg border p-4 shadow-lg ${styles[variant]}`} role={variant === 'error' ? 'alert' : 'status'}>
    <p className="flex-1 text-sm font-medium">{message}</p>
    <button type="button" onClick={onClose} aria-label="Fechar mensagem" className="font-bold">×</button>
  </div>;
}
