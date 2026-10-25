import type { ButtonHTMLAttributes, PropsWithChildren } from 'react';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';

const variants: Record<Variant, string> = {
  primary: 'bg-brand text-white hover:bg-teal-800',
  secondary: 'bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50',
  danger: 'bg-rose-700 text-white hover:bg-rose-800',
  ghost: 'text-slate-600 hover:bg-slate-100'
};

export function Button({ children, variant = 'primary', className = '', ...props }: PropsWithChildren<ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }>) {
  return <button className={`rounded-lg px-4 py-2 text-sm font-semibold transition ${variants[variant]} ${className}`} {...props}>{children}</button>;
}
