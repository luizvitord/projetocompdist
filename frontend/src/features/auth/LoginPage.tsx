import { FormEvent, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Button } from '../../components/ui/Button';
import { signIn } from './demoAuth';

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [message, setMessage] = useState('');

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const success = signIn(String(form.get('email') ?? ''), String(form.get('password') ?? ''));
    if (!success) {
      setMessage('Use as credenciais demonstrativas informadas abaixo.');
      return;
    }
    const destination = (location.state as { from?: string } | null)?.from ?? '/dashboard';
    navigate(destination, { replace: true });
  }

  return <main className="grid min-h-screen place-items-center bg-slate-100 p-6">
    <section className="w-full max-w-md rounded-2xl bg-white p-8 shadow-xl shadow-slate-300/50">
      <p className="text-sm font-bold uppercase tracking-[0.18em] text-teal-700">DistriSchool</p>
      <h1 className="mt-3 text-2xl font-bold text-navy">Acesso da coordenação</h1>
      <p className="mt-2 text-sm text-slate-600">Entre para administrar a vida escolar.</p>
      <form className="mt-6 space-y-4" onSubmit={submit}>
        <label className="block text-sm font-medium text-slate-700">E-mail
          <input required name="email" type="email" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="coordenacao@distrischool.com" />
        </label>
        <label className="block text-sm font-medium text-slate-700">Senha
          <input required name="password" type="password" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="••••••" />
        </label>
        {message ? <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{message}</p> : null}
        <Button className="w-full" type="submit">Entrar</Button>
      </form>
      <p className="mt-6 rounded-lg bg-slate-50 p-3 text-xs text-slate-600">Demonstração: <strong>coordenacao@distrischool.com</strong> · <strong>123456</strong></p>
    </section>
  </main>;
}
