import { FormEvent, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Button } from '../../components/ui/Button';
import { isApiProblem } from '../../services/http';
import { signIn } from './authApi';

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setMessage('');
    try {
      await signIn(String(form.get('email') ?? ''), String(form.get('password') ?? ''));
      const destination = (location.state as { from?: string } | null)?.from ?? '/dashboard';
      navigate(destination, { replace: true });
    } catch (error) {
      setMessage(isApiProblem(error) && error.status === 401
        ? error.detail
        : 'Não foi possível entrar agora. Tente novamente em instantes.');
      setSubmitting(false);
    }
  }

  return <main className="grid min-h-screen place-items-center bg-slate-100 p-6">
    <section className="w-full max-w-md rounded-2xl bg-white p-8 shadow-xl shadow-slate-300/50">
      <p className="text-sm font-bold uppercase tracking-[0.18em] text-teal-700">DistriSchool</p>
      <h1 className="mt-3 text-2xl font-bold text-navy">Acesso ao DistriSchool</h1>
      <p className="mt-2 text-sm text-slate-600">Entre com seu e-mail e senha.</p>
      <form className="mt-6 space-y-4" onSubmit={submit}>
        <label className="block text-sm font-medium text-slate-700">E-mail
          <input required name="email" type="email" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="coordenacao@distrischool.com" />
        </label>
        <label className="block text-sm font-medium text-slate-700">Senha
          <input required name="password" type="password" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="••••••" />
        </label>
        {message ? <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{message}</p> : null}
        <Button className="w-full" type="submit" disabled={submitting}>{submitting ? 'Entrando…' : 'Entrar'}</Button>
      </form>
      <p className="mt-6 rounded-lg bg-slate-50 p-3 text-xs text-slate-600">Primeiro acesso da coordenação: <strong>coordenacao@distrischool.com</strong> · <strong>123456</strong></p>
    </section>
  </main>;
}
